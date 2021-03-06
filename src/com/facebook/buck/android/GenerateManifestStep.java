/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.android;

import com.android.common.utils.ILogger;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.facebook.buck.android.apkmodule.APKModule;
import com.facebook.buck.core.build.execution.context.ExecutionContext;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GenerateManifestStep implements Step {

  private final ProjectFilesystem filesystem;
  private final Path skeletonManifestPath;
  private final ImmutableSet<Path> libraryManifestPaths;
  private final Path outManifestPath;
  private final Path mergeReportPath;
  private final APKModule module;

  public GenerateManifestStep(
      ProjectFilesystem filesystem,
      Path skeletonManifestPath,
      APKModule module,
      ImmutableSet<Path> libraryManifestPaths,
      Path outManifestPath,
      Path mergeReportPath) {
    this.filesystem = filesystem;
    this.skeletonManifestPath = skeletonManifestPath;
    this.module = module;
    this.libraryManifestPaths = ImmutableSet.copyOf(libraryManifestPaths);
    this.outManifestPath = outManifestPath;
    this.mergeReportPath = mergeReportPath;
  }

  @Override
  public StepExecutionResult execute(ExecutionContext context) throws IOException {

    if (skeletonManifestPath.getNameCount() == 0) {
      throw new HumanReadableException("Skeleton manifest filepath is missing");
    }

    if (outManifestPath.getNameCount() == 0) {
      throw new HumanReadableException("Output Manifest filepath is missing");
    }

    Path resolvedOutManifestPath = filesystem.resolve(outManifestPath);
    Files.createParentDirs(resolvedOutManifestPath.toFile());

    List<File> libraryManifestFiles = new ArrayList<>();

    for (Path path : libraryManifestPaths) {
      Path manifestPath = filesystem.getPathForRelativeExistingPath(path).toAbsolutePath();
      libraryManifestFiles.add(manifestPath.toFile());
    }

    File skeletonManifestFile =
        filesystem.getPathForRelativeExistingPath(skeletonManifestPath).toAbsolutePath().toFile();
    BuckEventAndroidLogger logger = new ManifestMergerLogger(context.getBuckEventBus());

    MergingReport mergingReport =
        mergeManifests(skeletonManifestFile, libraryManifestFiles, logger);

    String xmlText = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED);
    if (context.getPlatform() == Platform.WINDOWS) {
      // Convert line endings to Lf on Windows.
      xmlText = xmlText.replace("\r\n", "\n");
    }
    filesystem.writeContentsToPath(xmlText, resolvedOutManifestPath);

    return StepExecutionResults.SUCCESS;
  }

  private MergingReport mergeManifests(
      File mainManifestFile, List<File> libraryManifestFiles, BuckEventAndroidLogger logger) {
    try {
      ManifestMerger2.Invoker<?> manifestInvoker =
          ManifestMerger2.newMerger(
              mainManifestFile, logger, ManifestMerger2.MergeType.APPLICATION);
      if (!module.isRootModule()) {
        manifestInvoker.setPlaceHolderValue("split", module.getName());
      } else {
        manifestInvoker.withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT);
      }

      MergingReport mergingReport =
          manifestInvoker
              .withFeatures(
                  ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS,
                  ManifestMerger2.Invoker.Feature.SKIP_BLAME)
              .addLibraryManifests(Iterables.toArray(libraryManifestFiles, File.class))
              .setMergeReportFile(mergeReportPath.toFile())
              .merge();
      if (mergingReport.getResult().isError()) {
        for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
          logger.error(null, record.toString());
        }
        throw new HumanReadableException("Error generating manifest file");
      }

      return mergingReport;
    } catch (ManifestMerger2.MergeFailureException e) {
      throw new HumanReadableException(
          e.getCause(), "Error generating manifest file: %s", e.getMessage());
    }
  }

  @Override
  public String getDescription(ExecutionContext context) {
    return String.format("generate-manifest %s", skeletonManifestPath);
  }

  @Override
  public String getShortName() {
    return "generate_manifest";
  }

  private static class ManifestMergerLogger extends BuckEventAndroidLogger implements ILogger {
    public ManifestMergerLogger(BuckEventBus eventBus) {
      super(eventBus);
    }

    @Override
    public void info(String msgFormat, Object... args) {
      // suppress
    }

    @Override
    public void verbose(String msgFormat, Object... args) {
      // suppress
    }
  }
}
