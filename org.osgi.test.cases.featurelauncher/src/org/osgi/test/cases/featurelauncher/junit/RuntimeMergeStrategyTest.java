/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
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
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/

package org.osgi.test.cases.featurelauncher.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureBundle;
import org.osgi.service.feature.FeatureConfiguration;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.runtime.InstalledBundle;
import org.osgi.service.featurelauncher.runtime.MergeOperationType;
import org.osgi.service.featurelauncher.runtime.RuntimeBundleMerge;
import org.osgi.service.featurelauncher.runtime.RuntimeBundleMerge.BundleMapping;
import org.osgi.service.featurelauncher.runtime.RuntimeBundleMerge.FeatureBundleDefinition;
import org.osgi.service.featurelauncher.runtime.RuntimeConfigurationMerge;
import org.osgi.service.featurelauncher.runtime.RuntimeConfigurationMerge.FeatureConfigurationDefinition;
import org.osgi.service.featurelauncher.runtime.RuntimeMerges;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.5.1.6: the standard merge strategies provided by
 * {@link RuntimeMerges}. These drive the merge functions directly with crafted
 * inputs and assert their results, rather than only checking the factory methods
 * return non-null (the end-to-end behaviour through the runtime is covered by
 * {@link RuntimeBundleMergeBehaviourTest} and {@code RuntimeInstallTest}).
 */
public class RuntimeMergeStrategyTest {

	@InjectService
	FeatureService fs;

	ID id(String coords) {
		return fs.getIDfromMavenCoordinates(coords);
	}

	Feature feature(String coords) {
		return fs.getBuilderFactory().newFeatureBuilder(id(coords)).build();
	}

	// --- 160.5.1.6.1: RuntimeMerges.preferExistingBundles() ---

	@Nested
	class PreferExistingBundles {

		private FeatureBundle featureBundle(ID bundleId) {
			FeatureBundle fb = mock(FeatureBundle.class);
			when(fb.getID()).thenReturn(bundleId);
			return fb;
		}

		private InstalledBundle installedBundle(ID bundleId, Version version,
				List<ID> owningFeatures) {
			Bundle bundle = mock(Bundle.class);
			when(bundle.getVersion()).thenReturn(version);
			InstalledBundle ib = mock(InstalledBundle.class);
			when(ib.getBundleId()).thenReturn(bundleId);
			when(ib.getBundle()).thenReturn(bundle);
			when(ib.getOwningFeatures()).thenReturn(owningFeatures);
			return ib;
		}

		@Test
		void sameMajorExistingNotLower_keepsExistingOnly() {
			// An installed 1.5.0 and a candidate 1.2.0 (same major, existing
			// minor is higher): the existing bundle is kept and the candidate is
			// NOT installed.
			RuntimeBundleMerge merge = RuntimeMerges.preferExistingBundles();
			ID existingId = id("g:a:1.5.0");
			ID candidateId = id("g:a:1.2.0");
			Feature feature = feature("g:f:1.0.0");
			InstalledBundle existing = installedBundle(existingId,
					new Version(1, 5, 0), Collections.singletonList(id("g:owner:1.0.0")));

			List<BundleMapping> result = merge
					.mergeBundle(MergeOperationType.INSTALL, feature,
							featureBundle(candidateId), Collections.singletonList(existing),
							Collections.<FeatureBundleDefinition> emptyList())
					.collect(Collectors.toList());

			List<ID> mappedIds = result.stream().map(bm -> bm.bundleId)
					.collect(Collectors.toList());
			assertThat(mappedIds).contains(existingId)
					.doesNotContain(candidateId);
		}

		@Test
		void differentMajor_installsCandidate() {
			// A candidate with a different major version from every installed
			// bundle is installed in addition to the existing ones.
			RuntimeBundleMerge merge = RuntimeMerges.preferExistingBundles();
			ID existingId = id("g:a:1.0.0");
			ID candidateId = id("g:a:2.0.0");
			Feature feature = feature("g:f:1.0.0");
			InstalledBundle existing = installedBundle(existingId,
					new Version(1, 0, 0), Collections.singletonList(id("g:owner:1.0.0")));

			List<BundleMapping> result = merge
					.mergeBundle(MergeOperationType.INSTALL, feature,
							featureBundle(candidateId), Collections.singletonList(existing),
							Collections.<FeatureBundleDefinition> emptyList())
					.collect(Collectors.toList());

			List<ID> mappedIds = result.stream().map(bm -> bm.bundleId)
					.collect(Collectors.toList());
			assertThat(mappedIds).contains(existingId, candidateId);
			// The candidate is owned by the operated feature.
			assertThat(result.stream()
					.filter(bm -> candidateId.equals(bm.bundleId))
					.findFirst()
					.orElseThrow(() -> new AssertionError("candidate mapping missing"))
					.owningFeatures).contains(feature.getID());
		}

		@Test
		void removeOperation_keepsOwnedBundles() {
			// REMOVE is non-invasive: installed bundles that still have owners
			// are mapped unchanged.
			RuntimeBundleMerge merge = RuntimeMerges.preferExistingBundles();
			ID existingId = id("g:a:1.0.0");
			ID ownerId = id("g:owner:1.0.0");
			Feature feature = feature("g:f:1.0.0");
			InstalledBundle existing = installedBundle(existingId,
					new Version(1, 0, 0), Collections.singletonList(ownerId));

			List<BundleMapping> result = merge
					.mergeBundle(MergeOperationType.REMOVE, feature,
							featureBundle(id("g:a:1.0.0")), Collections.singletonList(existing),
							Collections.<FeatureBundleDefinition> emptyList())
					.collect(Collectors.toList());

			assertThat(result).singleElement()
					.satisfies(bm -> {
						assertThat(bm.bundleId).isEqualTo(existingId);
						assertThat(bm.owningFeatures).containsExactly(ownerId);
					});
		}
	}

	// --- 160.5.1.6.2: RuntimeMerges.replaceExistingProperties() ---

	@Nested
	class ReplaceExistingProperties {

		FeatureConfiguration config(String pid, String key, Object value) {
			return fs.getBuilderFactory()
					.newConfigurationBuilder(pid)
					.addValue(key, value)
					.build();
		}

		@Test
		void install_overlaysNewValues() {
			// INSTALL replaces the configuration values with the new ones.
			RuntimeConfigurationMerge merge = RuntimeMerges
					.replaceExistingProperties();
			Feature feature = feature("g:f:1.0.0");
			FeatureConfiguration toMerge = config("test.pid", "key", "new");

			Map<String,Object> result = merge.mergeConfiguration(
					MergeOperationType.INSTALL, feature, toMerge, null,
					Collections.<FeatureConfigurationDefinition> emptyList());

			assertThat(result).containsEntry("key", "new");
		}

		@Test
		void remove_noPreviousConfig_returnsNullToDelete() {
			// REMOVE with no remaining contributing features returns null, which
			// signals the configuration should be deleted.
			RuntimeConfigurationMerge merge = RuntimeMerges
					.replaceExistingProperties();
			Feature feature = feature("g:f:1.0.0");
			FeatureConfiguration toMerge = config("test.pid", "key", "value");

			Map<String,Object> result = merge.mergeConfiguration(
					MergeOperationType.REMOVE, feature, toMerge, null,
					Collections.<FeatureConfigurationDefinition> emptyList());

			assertThat(result).isNull();
		}
	}

	// --- 160.5: MergeOperationType enum ---

	@Nested
	class MergeOperationTypes {

		@Test
		void enumValuesComplete() {
			assertThat(MergeOperationType.values()).containsExactlyInAnyOrder(
					MergeOperationType.INSTALL, MergeOperationType.UPDATE,
					MergeOperationType.REMOVE);
		}
	}

	// --- 160.5: RuntimeMerges.getOSGiVersion() ---

	@Nested
	class GetOSGiVersion {

		@Test
		void getOSGiVersion_validId_returnsVersion() {
			Version v = RuntimeMerges.getOSGiVersion(id("g:a:1.2.3"));
			assertThat(v).isEqualTo(new Version(1, 2, 3));
		}

		@Test
		void getOSGiVersion_snapshot_stripsSuffix() {
			// The -SNAPSHOT suffix is stripped before parsing.
			Version v = RuntimeMerges.getOSGiVersion(id("g:a:1.2.3-SNAPSHOT"));
			assertThat(v.getMajor()).isEqualTo(1);
			assertThat(v.getMinor()).isEqualTo(2);
			assertThat(v.getMicro()).isEqualTo(3);
		}

		@Test
		void getOSGiVersion_nonNumericQualifier_keptAsQualifier() {
			// Lenient parsing: a trailing non-numeric segment becomes the
			// qualifier rather than failing.
			Version v = RuntimeMerges.getOSGiVersion(id("g:a:1.0.0.beta"));
			assertThat(v.getMajor()).isEqualTo(1);
			assertThat(v.getQualifier()).isEqualTo("beta");
		}
	}
}
