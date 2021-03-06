// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "myMap.entrySet().toArray()")
public class MostlySingularMultiMap<K, V> implements Serializable {
  private static final long serialVersionUID = 2784473565881807109L;

  protected final Map<K, Object> myMap;

  public MostlySingularMultiMap() {
    myMap = createMap();
  }

  @NotNull
  protected Map<K, Object> createMap() {
    return new THashMap<K, Object>();
  }

  public void add(@NotNull K key, @NotNull V value) {
    Object current = myMap.get(key);
    if (current == null) {
      myMap.put(key, value);
    }
    else if (current instanceof MostlySingularMultiMap.ValueList) {
      //noinspection unchecked
      ValueList<Object> curList = (ValueList<Object>) current;
      curList.add(value);
    }
    else {
      ValueList<Object> newList = new ValueList<Object>();
      newList.add(current);
      newList.add(value);
      myMap.put(key, newList);
    }
  }

  public boolean remove(@NotNull K key, @NotNull V value) {
    Object current = myMap.get(key);
    if (current == null) {
      return false;
    }
    if (current instanceof MostlySingularMultiMap.ValueList) {
      ValueList curList = (ValueList) current;
      return curList.remove(value);
    }

    if (value.equals(current)) {
      myMap.remove(key);
      return true;
    }

    return false;
  }

  public boolean removeAllValues(@NotNull K key) {
    return myMap.remove(key) != null;
  }

  @NotNull
  public Set<K> keySet() {
    return myMap.keySet();
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  public boolean processForKey(@NotNull K key, @NotNull Processor<? super V> p) {
    return processValue(p, myMap.get(key));
  }

  @SuppressWarnings("unchecked")
  private boolean processValue(@NotNull Processor<? super V> p, Object v) {
    if (v instanceof MostlySingularMultiMap.ValueList) {
      for (Object o : (ValueList)v) {
        if (!p.process((V)o)) return false;
      }
    }
    else if (v != null) {
      return p.process((V)v);
    }

    return true;
  }

  public boolean processAllValues(@NotNull Processor<? super V> p) {
    for (Object v : myMap.values()) {
      if (!processValue(p, v)) return false;
    }

    return true;
  }

  public int size() {
    return myMap.size();
  }

  public boolean containsKey(@NotNull K key) {
    return myMap.containsKey(key);
  }

  public int valuesForKey(@NotNull K key) {
    Object current = myMap.get(key);
    if (current == null) return 0;
    if (current instanceof MostlySingularMultiMap.ValueList) return ((ValueList)current).size();
    return 1;
  }

  @NotNull
  public Iterable<V> get(@NotNull K name) {
    final Object value = myMap.get(name);
    return rawValueToCollection(value);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  protected List<V> rawValueToCollection(Object value) {
    if (value == null) return Collections.emptyList();

    if (value instanceof MostlySingularMultiMap.ValueList) {
      return (ValueList<V>)value;
    }

    return Collections.singletonList((V)value);
  }

  public void compact() {
    ((THashMap)myMap).compact();
    for (Object eachValue : myMap.values()) {
      if (eachValue instanceof MostlySingularMultiMap.ValueList) {
        ((ValueList)eachValue).trimToSize();
      }
    }
  }

  @Override
  public String toString() {
    return "{" + StringUtil.join(myMap.entrySet(), new Function<Map.Entry<K, Object>, String>() {
      @Override
      public String fun(Map.Entry<K, Object> entry) {
        Object value = entry.getValue();
        String s = (value instanceof MostlySingularMultiMap.ValueList ? ((ValueList)value) : Collections.singletonList(value)).toString();
        return entry.getKey() + ": " + s;
      }
    }, "; ") + "}";
  }

  public void clear() {
    myMap.clear();
  }

  @NotNull
  public static <K,V> MostlySingularMultiMap<K,V> emptyMap() {
    //noinspection unchecked
    return EMPTY;
  }

  @NotNull
  public static <K, V> MostlySingularMultiMap<K, V> newMap() {
    return new MostlySingularMultiMap<K, V>();
  }
  private static final MostlySingularMultiMap EMPTY = new EmptyMap();

  @SuppressWarnings("unchecked")
  public void addAll(MostlySingularMultiMap<K, V> other) {
    if (other instanceof EmptyMap) return;

    for (Map.Entry<K, Object> entry : other.myMap.entrySet()) {
      K key = entry.getKey();
      Object otherValue = entry.getValue();
      Object myValue = myMap.get(key);

      if (myValue == null) {
        if (otherValue instanceof MostlySingularMultiMap.ValueList) {
          myMap.put(key, new ValueList((ValueList)otherValue));
        }
        else {
          myMap.put(key, otherValue);
        }
      }
      else if (myValue instanceof MostlySingularMultiMap.ValueList) {
        ValueList myListValue = (ValueList)myValue;
        if (otherValue instanceof MostlySingularMultiMap.ValueList) {
          myListValue.addAll((ValueList)otherValue);
        }
        else {
          myListValue.add(otherValue);
        }
      }
      else {
        if (otherValue instanceof MostlySingularMultiMap.ValueList) {
          ValueList otherListValue = (ValueList)otherValue;
          ValueList newList = new ValueList(otherListValue.size() + 1);
          newList.add(myValue);
          newList.addAll(otherListValue);
          myMap.put(key, newList);
        }
        else {
          ValueList newList = new ValueList();
          newList.add(myValue);
          newList.add(otherValue);
          myMap.put(key, newList);
        }
      }
    }
  }

  // marker class to distinguish multi-values from single values in case client want to store collections as values.
  protected static class ValueList<V> extends ArrayList<V> {
    public ValueList() {
    }

    public ValueList(int initialCapacity) {
      super(initialCapacity);
    }

    public ValueList(@NotNull Collection<? extends V> c) {
      super(c);
    }
  }
  
  private static class EmptyMap extends MostlySingularMultiMap {
    @Override
    public void add(@NotNull Object key, @NotNull Object value) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean remove(@NotNull Object key, @NotNull Object value) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean removeAllValues(@NotNull Object key) {
      throw new IncorrectOperationException();
    }

    @Override
    public void clear() {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public Set keySet() {
      return Collections.emptySet();
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public boolean processForKey(@NotNull Object key, @NotNull Processor p) {
      return true;
    }

    @Override
    public boolean processAllValues(@NotNull Processor p) {
      return true;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public int valuesForKey(@NotNull Object key) {
      return 0;
    }

    @NotNull
    @Override
    public Iterable get(@NotNull Object name) {
      return ContainerUtil.emptyList();
    }
  }
}
