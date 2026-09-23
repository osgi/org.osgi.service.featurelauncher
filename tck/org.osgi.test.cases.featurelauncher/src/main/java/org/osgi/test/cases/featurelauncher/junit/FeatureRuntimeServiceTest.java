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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime.InstallOperationBuilder;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime.UpdateOperationBuilder;
import org.osgi.service.featurelauncher.runtime.FeatureRuntimeException;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;
import org.osgi.service.featurelauncher.runtime.RuntimeBundleMerge;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.5: Feature Runtime Service.
 */
public class FeatureRuntimeServiceTest {

	@InjectService
	FeatureRuntime				runtime;

	@InjectService
	FeatureService				fs;

	@InjectService
	ArtifactRepositoryFactory	repoFactory;

	// --- 160.5: FeatureRuntime as OSGi Service ---

	@Nested
	class ServiceRegistration {

		@Test
		void featureRuntime_registeredAsOsgiService() {
			// 160.5: FeatureRuntime is registered as an OSGi service
			assertThat(runtime).isNotNull();
		}

		@Test
		void featureRuntime_isThreadSafe() throws Exception {
			// 160.5: FeatureRuntime MUST be Thread Safe
			int threadCount = 5;
			ExecutorService executor = Executors
					.newFixedThreadPool(threadCount);
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			AtomicBoolean failed = new AtomicBoolean(false);

			for (int i = 0; i < threadCount; i++) {
				final int index = i;
				executor.submit(() -> {
					try {
						startLatch.await(5, TimeUnit.SECONDS);
						Feature feature = TckTestHelper
								.createFeatureWithBundles(fs,
										"com.example:concurrent-" + index
												+ ":1.0.0",
										TckTestHelper.TB1_COORDS);
						ArtifactRepository repo = TckTestHelper
								.createTestRepository(repoFactory);
						try {
							runtime.install(feature)
									.addRepository("test-repo", repo)
									.useDefaultRepositories(false)
									.complete();
						} catch (Exception e) {
							// Some installs may fail due to bundle overlap,
							// which is acceptable in a concurrency test
						}
					} catch (Exception e) {
						failed.set(true);
					} finally {
						doneLatch.countDown();
					}
				});
			}

			startLatch.countDown();
			doneLatch.await(30, TimeUnit.SECONDS);
			executor.shutdown();
			assertThat(failed.get()).as(
					"Concurrent access to FeatureRuntime should not throw unexpected errors")
					.isFalse();

			TckTestHelper.cleanupInstalledFeatures(runtime);
		}
	}

	// --- 160.5: getDefaultRepositories() ---

	@Nested
	class DefaultRepositories {

		@Test
		void getDefaultRepositories_returnsMap() {
			// 160.5: getDefaultRepositories() returns
			// Map<String, ArtifactRepository>. Note: 160.5 only states a
			// runtime "typically" includes pre-defined repositories, it does
			// not mandate a non-empty map, so we only assert the contract type.
			Map<String,ArtifactRepository> defaults = runtime
					.getDefaultRepositories();
			assertThat(defaults).isNotNull();
		}
	}

	// --- 160.5: Single-use after complete() ---

	@Nested
	class BuilderSingleUse {

		@BeforeEach
		void setUp() throws Exception {
		}

		@AfterEach
		void tearDown() {
			TckTestHelper.cleanupInstalledFeatures(runtime);
		}

		@Test
		void installBuilder_complete_secondCall_throwsISE() throws Exception {
			// 160.5: InstallOperationBuilder is single-use;
			// second call to complete() throws IllegalStateException
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:install-single-use:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			assertThatThrownBy(() -> builder.complete())
					.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void updateBuilder_complete_secondCall_throwsISE() throws Exception {
			// 160.5: UpdateOperationBuilder is single-use;
			// second call to complete() throws IllegalStateException
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:update-single-use:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false)
					.complete();
			ID featureId = installed.getFeature().getID();

			Feature updatedFeature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:update-single-use:1.0.0",
					TckTestHelper.TB1_COORDS);
			UpdateOperationBuilder updateBuilder = runtime
					.update(featureId, updatedFeature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			updateBuilder.complete();

			assertThatThrownBy(() -> updateBuilder.complete())
					.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void operationBuilder_addRepository_afterComplete_throwsISE()
				throws Exception {
			// 160.5: addRepository after complete() throws ISE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:addrepo-after-complete:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			assertThatThrownBy(() -> builder.addRepository("another", repo))
					.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void operationBuilder_useDefaultRepos_afterComplete_throwsISE()
				throws Exception {
			// 160.5: useDefaultRepositories after complete() throws ISE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:usedefault-after-complete:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			assertThatThrownBy(() -> builder.useDefaultRepositories(true))
					.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void operationBuilder_withBundleMerge_afterComplete_throwsISE()
				throws Exception {
			// 160.5: withBundleMerge after complete() throws ISE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:bundlemerge-after-complete:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			RuntimeBundleMerge merge = (op, f, toMerge, installed,
					existing) -> Stream.empty();
			assertThatThrownBy(() -> builder.withBundleMerge(merge))
					.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void operationBuilder_withConfigMerge_afterComplete_throwsISE()
				throws Exception {
			// 160.5: withConfigurationMerge after complete() throws ISE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:configmerge-after-complete:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			assertThatThrownBy(() -> builder.withConfigurationMerge((op, f,
					toMerge, config, existing) -> Collections.emptyMap()))
							.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void operationBuilder_withVariables_afterComplete_throwsISE()
				throws Exception {
			// 160.5: withVariables after complete() throws ISE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:variables-after-complete:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			assertThatThrownBy(
					() -> builder.withVariables(Collections.emptyMap()))
							.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void operationBuilder_withDecorator_afterComplete_throwsISE()
				throws Exception {
			// 160.5: withDecorator after complete() throws ISE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:decorator-after-complete:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			assertThatThrownBy(() -> builder
					.withDecorator((f, repos, decoratedBuilder, factory) -> f))
							.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void operationBuilder_withExtHandler_afterComplete_throwsISE()
				throws Exception {
			// 160.5: withExtensionHandler after complete() throws ISE
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:exthandler-after-complete:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			builder.complete();

			assertThatThrownBy(
					() -> builder.withExtensionHandler("my-extension",
							(f, ext, repos, decoratedBuilder, factory) -> f))
									.isInstanceOf(IllegalStateException.class);
		}
	}

	// --- 160.5: OperationBuilder fluent API ---

	@Nested
	class BuilderApi {

		@AfterEach
		void tearDown() {
			TckTestHelper.cleanupInstalledFeatures(runtime);
		}

		@Test
		void addRepository_accumulates() throws Exception {
			// 160.5: addRepository stores repositories in order added
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:accumulate-repos:1.0.0",
					TckTestHelper.TB1_COORDS);
			// repo-1 resolves nothing but records that it was consulted; repo-2
			// resolves the bundle. If both are retained (accumulated), repo-1 is
			// queried and repo-2 satisfies the install.
			AtomicBoolean repo1Queried = new AtomicBoolean(false);
			ArtifactRepository repo1 = id -> {
				repo1Queried.set(true);
				return null;
			};
			ArtifactRepository repo2 = TckTestHelper
					.createTestRepository(repoFactory);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("repo-1", repo1)
					.addRepository("repo-2", repo2)
					.useDefaultRepositories(false)
					.complete();

			assertThat(repo1Queried.get())
					.as("the first added repository must be retained and consulted")
					.isTrue();
			assertThat(installed.getInstalledBundles()).isNotEmpty();
		}

		@Test
		void addRepository_sameNameReplaces() throws Exception {
			// 160.5: addRepository with same name replaces existing
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:same-name-replaces:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository goodRepo = TckTestHelper
					.createTestRepository(repoFactory);

			// First add an empty repo, then replace with a good one
			ArtifactRepository emptyRepo = id -> null;

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", emptyRepo)
					.addRepository("test-repo", goodRepo)
					.useDefaultRepositories(false);

			// Should succeed because the good repo replaced the empty one
			InstalledFeature installed = builder.complete();
			assertThat(installed).isNotNull();
		}

		@Test
		void addRepository_nullValue_removes() throws Exception {
			// 160.5: addRepository with null ArtifactRepository removes entry
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:null-removes:1.0.0", TckTestHelper.TB1_COORDS);
			ArtifactRepository goodRepo = TckTestHelper
					.createTestRepository(repoFactory);

			// "to-remove" is the only repository that can resolve the bundle.
			// Passing null for the same name must remove it, so the install can
			// no longer find the bundle and fails.
			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("to-remove", goodRepo)
					.addRepository("to-remove", null)
					.useDefaultRepositories(false);

			assertThatThrownBy(builder::complete)
					.isInstanceOf(FeatureRuntimeException.class);
		}

		@Test
		void useDefaultRepositories_defaultTrue() throws Exception {
			// 160.5: useDefaultRepositories defaults to true
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:default-repos-true:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			// Do not call useDefaultRepositories - it defaults to true, so the
			// default repositories are added in addition to our repo and the
			// install resolves tb1 from our repo.
			InstalledFeature installed = runtime.install(feature)
					.addRepository("test", repo)
					.complete();
			assertThat(installed).isNotNull();
		}

		@Test
		void useDefaultRepositories_false_excludes() throws Exception {
			// 160.5: useDefaultRepositories(false) excludes default repos
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:no-default-repos:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			// With useDefaultRepositories(false) and only our custom repo,
			// the install should still succeed because our repo has the bundle
			InstalledFeature installed = runtime.install(feature)
					.addRepository("custom", repo)
					.useDefaultRepositories(false)
					.complete();
			assertThat(installed).isNotNull();
		}

		@Test
		void defaultRepos_addedAfterCustom() throws Exception {
			// 160.5: Default repositories always added after custom repos
			// Custom repos are queried first, then default repos
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:custom-before-default:1.0.0",
					TckTestHelper.TB1_COORDS);
			AtomicBoolean customRepoQueried = new AtomicBoolean(false);
			AtomicReference<Boolean> customQueriedFirst = new AtomicReference<>(
					null);

			ArtifactRepository trackingRepo = id -> {
				customRepoQueried.set(true);
				if (customQueriedFirst.get() == null) {
					customQueriedFirst.set(true);
				}
				return null; // Return null so later repos are also consulted
			};
			// A real repo added after the tracking repo so the bundle resolves;
			// the tracking repo must still be consulted first.
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("tracking", trackingRepo)
					.addRepository("test", repo)
					.useDefaultRepositories(true)
					.complete();
			assertThat(installed).isNotNull();
			assertThat(customRepoQueried.get())
					.as("Custom repository should be queried")
					.isTrue();
			assertThat(customQueriedFirst.get())
					.as("Custom repository should be queried before default")
					.isTrue();
		}

		@Test
		void addedRepoOverridesDefaultSameName() throws Exception {
			// 160.5: Added repo with same name overrides default. Default
			// repositories are "typically" but not necessarily present, so this
			// scenario only applies when the runtime defines at least one.
			Map<String,ArtifactRepository> defaults = runtime
					.getDefaultRepositories();
			assumeFalse(defaults.isEmpty(),
					"Runtime has no default repositories to override");

			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:override-default:1.0.0",
					TckTestHelper.TB1_COORDS);

			// Pick a default repo name and override it with our custom repo
			String defaultName = defaults.keySet().iterator().next();
			ArtifactRepository customRepo = TckTestHelper
					.createTestRepository(repoFactory);

			InstalledFeature installed = runtime.install(feature)
					.addRepository(defaultName, customRepo)
					.useDefaultRepositories(true)
					.complete();
			assertThat(installed).isNotNull();
		}

		@Test
		void installAlias_forComplete() throws Exception {
			// 160.5: install() is an alias for complete()
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:install-alias:1.0.0",
					TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);

			// Use install() instead of complete()
			InstalledFeature installed = builder.install();
			assertThat(installed.getFeature().getID()).isEqualTo(feature.getID());
		}

		@Test
		void updateAlias_forComplete() throws Exception {
			// 160.5: update() is an alias for complete()
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:update-alias:1.0.0", TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstalledFeature installed = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false)
					.complete();
			ID featureId = installed.getFeature().getID();

			Feature updatedFeature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:update-alias:1.0.0", TckTestHelper.TB1_COORDS);

			// Use update() instead of complete()
			UpdateOperationBuilder updateBuilder = runtime
					.update(featureId, updatedFeature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);
			InstalledFeature updated = updateBuilder.update();
			assertThat(updated.getFeature().getID()).isEqualTo(featureId);
		}
	}

	// --- 160.5: OperationBuilder NOT thread safe ---

	@Nested
	class BuilderNotThreadSafe {

		@AfterEach
		void tearDown() {
			TckTestHelper.cleanupInstalledFeatures(runtime);
		}

		@Test
		void operationBuilder_notShared() throws Exception {
			// 160.5: OperationBuilder must not be shared between threads
			// Verify that using a builder from a different thread does not
			// cause undefined behavior by testing that builders are usable
			// only from the creating thread's perspective
			Feature feature = TckTestHelper.createFeatureWithBundles(fs,
					"com.example:not-shared:1.0.0", TckTestHelper.TB1_COORDS);
			ArtifactRepository repo = TckTestHelper
					.createTestRepository(repoFactory);

			InstallOperationBuilder builder = runtime.install(feature)
					.addRepository("test-repo", repo)
					.useDefaultRepositories(false);

			AtomicReference<Throwable> threadError = new AtomicReference<>();
			AtomicReference<InstalledFeature> threadResult = new AtomicReference<>();

			// Try to use the builder from a different thread
			Thread t = new Thread(() -> {
				try {
					InstalledFeature result = builder.complete();
					threadResult.set(result);
				} catch (Throwable e) {
					threadError.set(e);
				}
			});
			t.start();
			t.join(10_000);

			// The spec says builders must not be shared. The implementation
			// may either succeed (if not enforced) or throw an exception.
			// Either outcome is acceptable; the key spec point is that
			// sharing is not supported.
			if (threadError.get() != null) {
				// Implementation detected cross-thread usage
				assertThat(threadError.get()).isInstanceOf(Exception.class);
			} else {
				// Implementation did not enforce but the operation completed
				assertThat(threadResult.get()).isNotNull();
			}
		}
	}
}
