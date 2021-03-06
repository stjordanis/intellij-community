// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.google.common.collect.Lists;
import com.intellij.json.psi.JsonProperty;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

class ThemeJsonUtil {

  static String getParentNames(@NotNull JsonProperty property) {
    List<JsonProperty> parentProperties = PsiTreeUtil.collectParents(property, JsonProperty.class, false, e -> {
      //TODO check that it is TOP-LEVEL 'ui'  property
      return e instanceof JsonProperty && "ui".equals(((JsonProperty)e).getName());
    });

    return Lists.reverse(parentProperties).stream()
      .map(p -> p.getName())
      .collect(Collectors.joining("."));
  }

  static boolean isThemeFilename(@NotNull String fileName) {
    return fileName.endsWith(".theme.json");
  }
}
