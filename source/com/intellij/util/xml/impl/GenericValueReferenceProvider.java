/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class GenericValueReferenceProvider implements PsiReferenceProvider {
  @NotNull
  public GenericReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof XmlTag || element instanceof XmlAttributeValue)) return GenericReference.EMPTY_ARRAY;
    PsiElement originalElement = element.getUserData(PsiUtil.ORIGINAL_KEY);
    if (originalElement != null){
      element = originalElement;
    }

    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);

    final DomElement domElement = DomManager.getDomManager(element.getManager().getProject()).getDomElement(tag);
    if (domElement == null) return GenericReference.EMPTY_ARRAY;

    if (element instanceof XmlAttributeValue) {
      final XmlAttributeValue value = (XmlAttributeValue)element;
      final String name = ((XmlAttribute)value.getParent()).getLocalName();
      final DomAttributeChildDescription childDescription = domElement.getGenericInfo().getAttributeChildDescription(name);
      if (childDescription != null) {
        final GenericReference reference = createReference(childDescription.getValues(domElement).get(0));
        if (reference != null) {
          return new GenericReference[]{reference};
        }
      }
    } else {
      final GenericDomValueReference reference = createReference(domElement);
      if (reference != null) {
        return new GenericReference[]{reference};
      }
    }

    return GenericReference.EMPTY_ARRAY;
  }

  @Nullable
  private GenericDomValueReference createReference(DomElement element) {
    if (!(element instanceof GenericDomValue)) return null;

    if (element instanceof GenericAttributeValue) {
      if (((GenericAttributeValue)element).getXmlAttributeValue() == null) return null;
    }
    else if (element.getXmlTag().getValue().getTextElements().length == 0) return null;

    GenericDomValue domElement = (GenericDomValue) element;
    final Class parameter = DomUtil.getGenericValueType(domElement.getDomElementType());
    if (PsiType.class.isAssignableFrom(parameter)) {
      return new PsiTypeReference(this, (GenericDomValue<PsiType>)domElement);
    }
    if (PsiClass.class.isAssignableFrom(parameter)) {
      return new PsiClassReference(this, (GenericDomValue<PsiClass>)domElement);
    }
    if (Integer.class.isAssignableFrom(parameter)) {
      return new GenericDomValueReference(this, domElement) {
        public Object[] getVariants() {
          return new Object[]{"239", "42"};
        }
      };
    }
    if (!String.class.isAssignableFrom(parameter)) {
      return new GenericDomValueReference(this, domElement);
    }
    return null;
  }


  @NotNull
  public GenericReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public GenericReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
