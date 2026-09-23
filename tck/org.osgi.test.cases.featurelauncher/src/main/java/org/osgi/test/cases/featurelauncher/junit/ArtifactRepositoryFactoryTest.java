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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryConstants;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.2: Artifact Repositories -
 * ArtifactRepositoryFactory.
 */
public class ArtifactRepositoryFactoryTest {

	@InjectService
	ArtifactRepositoryFactory	factory;

	@InjectService
	FeatureService				fs;

	// --- 160.2: createRepository(Path) - Local Maven 2 Repository ---

	@Nested
	class RepositoryFromPath {

		@Test
		void createRepository_validPath_succeeds(@TempDir
		Path tempDir) throws Exception {
			// 160.2: createRepository(Path) with valid directory
			ArtifactRepository repo = factory.createRepository(tempDir);
			assertThat(repo).isNotNull();
		}

		@Test
		void createRepository_nullPath_throwsNPE() {
			// 160.2: NullPointerException if the path is null
			assertThatThrownBy(() -> factory.createRepository((Path) null))
					.isInstanceOf(NullPointerException.class);
		}

		@Test
		void createRepository_nonExistentPath_throwsIAE() {
			// 160.2: IllegalArgumentException if path does not exist
			Path nonExistent = Paths.get(
					"/tmp/non-existent-repo-" + System.currentTimeMillis());
			assertThatThrownBy(() -> factory.createRepository(nonExistent))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void createRepository_fileNotDirectory_throwsIAE(@TempDir
		Path tempDir) throws Exception {
			// 160.2: IllegalArgumentException if path is not a directory
			Path file = tempDir.resolve("not-a-dir.txt");
			Files.write(file, "content".getBytes(StandardCharsets.UTF_8));
			assertThatThrownBy(() -> factory.createRepository(file))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void createRepository_localRepo_resolvesMaven2Layout()
				throws Exception {
			// 160.2: local repos use Maven 2 layout
			Path repoDir = TckTestHelper.createLocalMavenRepo();
			try {
				ArtifactRepository repo = factory.createRepository(repoDir);
				assertThat(repo).isNotNull();
				ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
				InputStream is = repo.getArtifact(id);
				assertThat(is).isNotNull();
				is.close();
			} finally {
				TckTestHelper.deleteDirectory(repoDir);
			}
		}
	}

	// --- 160.2: createRepository(URI, Map) - Remote/Local Repository ---

	@Nested
	class RepositoryFromUri {

		@Test
		void createRepository_fileScheme_succeeds(@TempDir
		Path tempDir) {
			// 160.2: file: URI scheme must be supported
			URI fileUri = tempDir.toUri();
			ArtifactRepository repo = factory.createRepository(fileUri,
					Collections.emptyMap());
			assertThat(repo).isNotNull();
		}

		@Test
		void createRepository_nullUri_throwsNPE() {
			// 160.2: NullPointerException if URI is null
			assertThatThrownBy(() -> factory.createRepository((URI) null,
					Collections.emptyMap()))
							.isInstanceOf(NullPointerException.class);
		}

		@Test
		void createRepository_unsupportedScheme_throwsIAE() {
			// 160.2: IllegalArgumentException if scheme not supported
			URI ftpUri = URI.create("ftp://example.com/repo");
			assertThatThrownBy(() -> factory.createRepository(ftpUri,
					Collections.emptyMap()))
							.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void createRepository_unknownProperties_ignored(@TempDir
		Path tempDir) {
			// 160.2: unknown configuration keys must be ignored
			URI fileUri = tempDir.toUri();
			Map<String,Object> props = new HashMap<>();
			props.put("com.example.unknown.key", "some-value");
			ArtifactRepository repo = factory.createRepository(fileUri, props);
			assertThat(repo).isNotNull();
		}

		@ParameterizedTest
		@ValueSource(strings = {
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_NAME,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_USER,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_PASSWORD,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_BEARER_TOKEN,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_SNAPSHOTS_ENABLED,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_RELEASES_ENABLED,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_TRUST_STORE,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_TRUST_STORE_FORMAT,
				ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_TRUST_STORE_PASSWORD
		})
		void createRepository_standardProperty_accepted(String propertyKey,
				@TempDir
				Path tempDir) {
			// 160.2: each standard configuration property key MUST be
			// supported
			URI fileUri = tempDir.toUri();
			Map<String,Object> props = new HashMap<>();
			props.put(propertyKey, "test-value");
			// Should not throw - standard properties must be accepted
			ArtifactRepository repo = factory.createRepository(fileUri, props);
			assertThat(repo).isNotNull();
		}
	}
}
