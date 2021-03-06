// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.util.Key;

/**
 * An adapter for line handler
 */
@Deprecated
public class GitLineHandlerAdapter implements GitLineHandlerListener {
  /**
   * {@inheritDoc}
   */
  @Override
  public void onLineAvailable(final String line, final Key outputType) {
    // do nothing
  }
}
