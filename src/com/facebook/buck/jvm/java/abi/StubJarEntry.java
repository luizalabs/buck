/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.java.abi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

public abstract class StubJarEntry {
  @Nullable
  static StubJarEntry of(LibraryReader input, Path path) throws IOException {
    if (isStubbableResource(input, path)) {
      return StubJarResourceEntry.of(input, path);
    } else if (input.isClass(path)) {
      return StubJarClassEntry.of(input, path);
    }

    return null;
  }

  public abstract void write(StubJarWriter writer) throws IOException;

  private static boolean isStubbableResource(LibraryReader input, Path path) {
    return input.isResource(path) && !path.endsWith("META-INF" + File.separator + "MANIFEST.MF");
  }
}
