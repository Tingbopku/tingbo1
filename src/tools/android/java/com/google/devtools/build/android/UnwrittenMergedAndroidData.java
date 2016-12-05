// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import com.android.ide.common.res2.MergingException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Merged Android Data that has yet to written into a {@link MergedAndroidData}.
 */
public class UnwrittenMergedAndroidData {

  private final Path manifest;
  private final ParsedAndroidData primary;
  private final ParsedAndroidData transitive;

  public static UnwrittenMergedAndroidData of(
      Path manifest, ParsedAndroidData resources, ParsedAndroidData deps) {
    return new UnwrittenMergedAndroidData(manifest, resources, deps);
  }

  private UnwrittenMergedAndroidData(
      Path manifest, ParsedAndroidData primary, ParsedAndroidData transitive) {
    this.manifest = manifest;
    this.primary = primary;
    this.transitive = transitive;
  }

  /**
   * Writes the android data to the filesystem.
   *
   * @param mergedDataWriter Destination writer.
   * @return A MergedAndroidData that is ready for further tool processing.
   * @throws IOException when something goes wrong while writing.
   * @throws MergingException when something goes wrong with the merge.
   */
  public MergedAndroidData write(AndroidDataWriter mergedDataWriter)
      throws IOException, MergingException {
    try {
      writeParsedAndroidData(primary, mergedDataWriter);
      writeParsedAndroidData(transitive, mergedDataWriter);
      return new MergedAndroidData(
          mergedDataWriter.resourceDirectory(),
          mergedDataWriter.assetDirectory(),
          this.manifest != null ? mergedDataWriter.copyManifest(this.manifest) : null);
    } finally {
      // Flush to make sure all writing is completed before returning a MergedAndroidData.
      // If resources aren't fully written, the MergedAndroidData might be invalid.
      mergedDataWriter.flush();
    }
  }

  private void writeParsedAndroidData(
      ParsedAndroidData resources, AndroidDataWritingVisitor mergedDataWriter)
      throws IOException, MergingException {
    for (Entry<DataKey, DataAsset> entry : resources.iterateAssetEntries()) {
      // TODO(corysmith): Resolve the nit of casting to a RelativeAssetPath by sorting
      // out the type structure and generics of DataKey, ParsedAndroidData, AndroidDataMerger and
      // MergeConflict.
      entry.getValue().writeAsset((RelativeAssetPath) entry.getKey(), mergedDataWriter);
    }
    for (Entry<DataKey, DataResource> entry : resources.iterateDataResourceEntries()) {
      // TODO(corysmith): Resolve the nit of casting to a FullyQualifiedName by sorting
      // out the type structure and generics of DataKey, ParsedAndroidData, AndroidDataMerger and
      // MergeConflict.
      entry.getValue().writeResource((FullyQualifiedName) entry.getKey(), mergedDataWriter);
    }
  }

  public void writeResourceClass(AndroidResourceClassWriter resourceClassWriter)
      throws IOException {
    writeResourceClassItems(primary, resourceClassWriter);
    writeResourceClassItems(transitive, resourceClassWriter);
    resourceClassWriter.flush();
  }

  private void writeResourceClassItems(
      ParsedAndroidData resources, AndroidResourceClassWriter resourceClassWriter)
      throws IOException {
    for (Entry<DataKey, DataResource> entry : resources.iterateDataResourceEntries()) {
      // TODO(corysmith): Resolve the nit of casting to a FullyQualifiedName by sorting
      // out the type structure and generics of DataKey, ParsedAndroidData, AndroidDataMerger and
      // MergeConflict.
      entry.getValue()
          .writeResourceToClass((FullyQualifiedName) entry.getKey(), resourceClassWriter);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("manifest", manifest)
        .add("primary", primary)
        .add("transitive", transitive)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof UnwrittenMergedAndroidData)) {
      return false;
    }
    UnwrittenMergedAndroidData that = (UnwrittenMergedAndroidData) other;
    return Objects.equals(manifest, that.manifest)
        && Objects.equals(primary, that.primary)
        && Objects.equals(transitive, that.transitive);
  }

  @Override
  public int hashCode() {
    return Objects.hash(manifest, primary, transitive);
  }

  @VisibleForTesting
  Path getManifest() {
    return manifest;
  }

  @VisibleForTesting
  ParsedAndroidData getPrimary() {
    return primary;
  }

  @VisibleForTesting
  ParsedAndroidData getTransitive() {
    return transitive;
  }

  public void serializeTo(AndroidDataSerializer serializer) {
    for (Entry<DataKey, DataAsset> entry : primary.iterateAssetEntries()) {
      serializer.queueForSerialization(entry.getKey(), entry.getValue());
    }
    for (Entry<DataKey, DataResource> entry : primary.iterateDataResourceEntries()) {
      serializer.queueForSerialization(entry.getKey(), entry.getValue());
    }
  }
}
