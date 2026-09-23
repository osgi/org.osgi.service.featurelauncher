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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.service.feature.BuilderFactory;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureBuilder;
import org.osgi.service.feature.FeatureBundle;
import org.osgi.service.feature.FeatureConfiguration;
import org.osgi.service.feature.FeatureConfigurationBuilder;
import org.osgi.service.feature.FeatureExtension;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.service.featurelauncher.runtime.FeatureRuntime;
import org.osgi.service.featurelauncher.runtime.InstalledFeature;

/**
 * Utility helper for Feature Launcher TCK tests.
 */
final class TckTestHelper {

	static final String	TB1_GROUP		= "org.osgi.test.cases.featurelauncher";
	static final String	TB1_ARTIFACT	= "tb1";
	static final String	TB1_VERSION		= "1.0.0";
	static final String	TB1_COORDS		= TB1_GROUP + ":" + TB1_ARTIFACT + ":"
			+ TB1_VERSION;

	static final String	TB2_GROUP		= "org.osgi.test.cases.featurelauncher";
	static final String	TB2_ARTIFACT	= "tb2";
	static final String	TB2_VERSION		= "1.0.0";
	static final String	TB2_COORDS		= TB2_GROUP + ":" + TB2_ARTIFACT + ":"
			+ TB2_VERSION;

	static final String	TB3_GROUP		= "org.osgi.test.cases.featurelauncher";
	static final String	TB3_ARTIFACT	= "tb3";
	static final String	TB3_VERSION		= "1.0.0";
	static final String	TB3_COORDS		= TB3_GROUP + ":" + TB3_ARTIFACT + ":"
			+ TB3_VERSION;

	// tb4 fails on start (see tb4.Activator), used for start-failure tests.
	static final String	TB4_GROUP		= "org.osgi.test.cases.featurelauncher";
	static final String	TB4_ARTIFACT	= "tb4";
	static final String	TB4_VERSION		= "1.0.0";
	static final String	TB4_COORDS		= TB4_GROUP + ":" + TB4_ARTIFACT + ":"
			+ TB4_VERSION;

	// tbm is staged at two versions sharing one symbolic name, for bundle-merge
	// tests. The embedded jars are tbmv1.jar (1.0.0) and tbmv2.jar (1.1.0).
	static final String	TBM_GROUP		= "org.osgi.test.cases.featurelauncher";
	static final String	TBM_ARTIFACT	= "tbm";
	static final String	TBM_SYMBOLIC_NAME	= "org.osgi.test.cases.featurelauncher.tbm";
	static final String	TBM_V1			= "1.0.0";
	static final String	TBM_V2			= "1.1.0";
	static final String	TBM_V1_COORDS	= TBM_GROUP + ":" + TBM_ARTIFACT + ":"
			+ TBM_V1;
	static final String	TBM_V2_COORDS	= TBM_GROUP + ":" + TBM_ARTIFACT + ":"
			+ TBM_V2;

	private TckTestHelper() {
		// utility class
	}

	/**
	 * Java 8 compatible replacement for Map.of().
	 */
	@SuppressWarnings("unchecked")
	static <K, V> Map<K,V> mapOf(Object... keyValues) {
		Map<K,V> map = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put((K) keyValues[i], (V) keyValues[i + 1]);
		}
		return Collections.unmodifiableMap(map);
	}

	/**
	 * Returns a Reader for a classpath resource containing Feature JSON.
	 */
	static Reader featureReader(String resourcePath) {
		return new InputStreamReader(
				TckTestHelper.class.getResourceAsStream(resourcePath),
				StandardCharsets.UTF_8);
	}

	/**
	 * Creates a minimal Feature with no bundles or configurations.
	 */
	static Feature createMinimalFeature(FeatureService fs, String mavenCoords) {
		BuilderFactory bf = fs.getBuilderFactory();
		return bf.newFeatureBuilder(fs.getIDfromMavenCoordinates(mavenCoords))
				.build();
	}

	/**
	 * Creates a Feature with the specified bundles (given as Maven
	 * coordinates).
	 */
	static Feature createFeatureWithBundles(FeatureService fs,
			String featureCoords, String... bundleCoords) {
		BuilderFactory bf = fs.getBuilderFactory();
		FeatureBuilder fb = bf
				.newFeatureBuilder(fs.getIDfromMavenCoordinates(featureCoords))
				.setComplete(true);
		for (String bc : bundleCoords) {
			FeatureBundle bundle = bf
					.newBundleBuilder(fs.getIDfromMavenCoordinates(bc))
					.build();
			fb.addBundles(bundle);
		}
		return fb.build();
	}

	/**
	 * Creates a Feature with bundles and a single configuration.
	 */
	static Feature createFeatureWithConfig(FeatureService fs,
			String featureCoords, String bundleCoords, String pid,
			Map<String,Object> props) {
		BuilderFactory bf = fs.getBuilderFactory();
		FeatureBundle bundle = bf
				.newBundleBuilder(fs.getIDfromMavenCoordinates(bundleCoords))
				.build();
		FeatureConfigurationBuilder configBuilder = bf
				.newConfigurationBuilder(pid);
		for (Map.Entry<String,Object> e : props.entrySet()) {
			configBuilder.addValue(e.getKey(), e.getValue());
		}
		FeatureConfiguration config = configBuilder.build();
		return bf.newFeatureBuilder(fs.getIDfromMavenCoordinates(featureCoords))
				.setComplete(true)
				.addBundles(bundle)
				.addConfigurations(config)
				.build();
	}

	/**
	 * Creates a Feature with a factory configuration.
	 */
	static Feature createFeatureWithFactoryConfig(FeatureService fs,
			String featureCoords, String bundleCoords, String factoryPid,
			String name, Map<String,Object> props) {
		BuilderFactory bf = fs.getBuilderFactory();
		FeatureBundle bundle = bf
				.newBundleBuilder(fs.getIDfromMavenCoordinates(bundleCoords))
				.build();
		FeatureConfigurationBuilder configBuilder = bf
				.newConfigurationBuilder(factoryPid, name);
		for (Map.Entry<String,Object> e : props.entrySet()) {
			configBuilder.addValue(e.getKey(), e.getValue());
		}
		FeatureConfiguration config = configBuilder.build();
		return bf.newFeatureBuilder(fs.getIDfromMavenCoordinates(featureCoords))
				.setComplete(true)
				.addBundles(bundle)
				.addConfigurations(config)
				.build();
	}

	/**
	 * Creates a Feature with a single bundle and a named (optional, text)
	 * extension, so that a registered {@code FeatureExtensionHandler} for that
	 * name is actually invoked during decoration.
	 */
	static Feature createFeatureWithExtension(FeatureService fs,
			String featureCoords, String bundleCoords, String extensionName,
			String text) {
		BuilderFactory bf = fs.getBuilderFactory();
		FeatureBundle bundle = bf
				.newBundleBuilder(fs.getIDfromMavenCoordinates(bundleCoords))
				.build();
		FeatureExtension extension = bf
				.newExtensionBuilder(extensionName, FeatureExtension.Type.TEXT,
						FeatureExtension.Kind.OPTIONAL)
				.addText(text)
				.build();
		return bf.newFeatureBuilder(fs.getIDfromMavenCoordinates(featureCoords))
				.setComplete(true)
				.addBundles(bundle)
				.addExtensions(extension)
				.build();
	}

	/**
	 * Creates a Feature with variables.
	 */
	static Feature createFeatureWithVariables(FeatureService fs,
			String featureCoords, Map<String,Object> variables) {
		BuilderFactory bf = fs.getBuilderFactory();
		return bf.newFeatureBuilder(fs.getIDfromMavenCoordinates(featureCoords))
				.addVariables(variables)
				.build();
	}

	/**
	 * Creates a temporary directory with Maven 2 repository layout containing
	 * the embedded test bundles (tb1.jar, tb2.jar, tb3.jar).
	 *
	 * @return Path to the temporary repository directory
	 */
	static Path createLocalMavenRepo() throws IOException {
		Path repoDir = Files.createTempDirectory("tck-maven-repo");

		copyBundleToMavenLayout(repoDir, "/tb1.jar", TB1_GROUP, TB1_ARTIFACT,
				TB1_VERSION);
		copyBundleToMavenLayout(repoDir, "/tb2.jar", TB2_GROUP, TB2_ARTIFACT,
				TB2_VERSION);
		copyBundleToMavenLayout(repoDir, "/tb3.jar", TB3_GROUP, TB3_ARTIFACT,
				TB3_VERSION);
		copyBundleToMavenLayout(repoDir, "/tb4.jar", TB4_GROUP, TB4_ARTIFACT,
				TB4_VERSION);
		copyBundleToMavenLayout(repoDir, "/tbmv1.jar", TBM_GROUP, TBM_ARTIFACT,
				TBM_V1);
		copyBundleToMavenLayout(repoDir, "/tbmv2.jar", TBM_GROUP, TBM_ARTIFACT,
				TBM_V2);

		return repoDir;
	}

	/**
	 * Copies a classpath resource to Maven 2 layout in the given repo
	 * directory.
	 */
	private static void copyBundleToMavenLayout(Path repoDir,
			String resourcePath, String groupId, String artifactId,
			String version) throws IOException {
		String groupPath = groupId.replace('.', '/');
		Path artifactDir = repoDir
				.resolve(groupPath + "/" + artifactId + "/" + version);
		Files.createDirectories(artifactDir);
		String fileName = artifactId + "-" + version + ".jar";
		try (InputStream is = TckTestHelper.class
				.getResourceAsStream(resourcePath)) {
			if (is != null) {
				Files.copy(is, artifactDir.resolve(fileName),
						StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/**
	 * Creates an ArtifactRepository backed by a local Maven repo containing the
	 * test bundles.
	 */
	static ArtifactRepository createTestRepository(
			ArtifactRepositoryFactory factory) throws IOException {
		Path repoDir = createLocalMavenRepo();
		return factory.createRepository(repoDir);
	}

	/**
	 * Cleans up all installed features that are not from initial launch.
	 */
	static void cleanupInstalledFeatures(FeatureRuntime runtime) {
		if (runtime == null) {
			return;
		}
		// Copy defensively: getInstalledFeatures() may return a live view and
		// remove() mutates it, which would throw ConcurrentModificationException
		// while iterating.
		List<InstalledFeature> features = new java.util.ArrayList<>(
				runtime.getInstalledFeatures());
		for (InstalledFeature f : features) {
			if (!f.isInitialLaunch()) {
				try {
					runtime.remove(f.getFeature().getID());
				} catch (Exception e) {
					// best effort cleanup
				}
			}
		}
	}

	/**
	 * Recursively deletes a directory tree.
	 */
	static void deleteDirectory(Path dir) throws IOException {
		if (dir != null && Files.exists(dir)) {
			Files.walk(dir)
					.sorted(java.util.Comparator.reverseOrder())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							// best effort
						}
					});
		}
	}
}
