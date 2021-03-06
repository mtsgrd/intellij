/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

/** Maps a source file to the blaze targets building that source file. */
public interface SourceToTargetProvider {

  ExtensionPointName<SourceToTargetProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SourceToTargetProvider");

  static boolean hasProvider() {
    return EP_NAME.getExtensions().length != 0;
  }

  /**
   * Returns the blaze targets provided by the first available {@link SourceToTargetProvider} able
   * to handle the given source file.
   *
   * <p>Future returns null if no provider was able to handle the given source file.
   */
  static ListenableFuture<List<TargetInfo>> findTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath) {
    Iterable<Future<List<TargetInfo>>> futures =
        Iterables.transform(
            Arrays.asList(EP_NAME.getExtensions()),
            f -> f.getTargetsBuildingSourceFile(project, workspaceRelativePath));
    return FuturesUtil.getFirstFutureSatisfyingPredicate(futures, Objects::nonNull);
  }

  /**
   * Query the blaze targets building the given source file.
   *
   * <p>Future returns null if this provider was unable to query the blaze targets.
   */
  Future<List<TargetInfo>> getTargetsBuildingSourceFile(
      Project project, String workspaceRelativePath);
}
