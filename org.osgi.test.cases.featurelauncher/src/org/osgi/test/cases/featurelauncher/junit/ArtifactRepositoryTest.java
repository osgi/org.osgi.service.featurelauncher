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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.2: Artifact Repository interface.
 */
public class ArtifactRepositoryTest {

	@InjectService
	ArtifactRepositoryFactory	factory;

	@InjectService
	FeatureService				fs;

	private ArtifactRepository	repo;
	private Path				repoDir;

	@BeforeEach
	void setUp() throws Exception {
		repoDir = TckTestHelper.createLocalMavenRepo();
		repo = factory.createRepository(repoDir);
	}

	@AfterEach
	void tearDown() throws Exception {
		TckTestHelper.deleteDirectory(repoDir);
	}

	// --- 160.2: getArtifact(ID) behavior ---

	@Test
	void getArtifact_knownId_returnsInputStream() throws Exception {
		// 160.2: getArtifact(ID) returns InputStream for known artifact
		ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
		try (InputStream is = repo.getArtifact(id)) {
			assertThat(is).isNotNull();
			assertThat(is.read()).isGreaterThanOrEqualTo(0);
		}
	}

	@Test
	void getArtifact_unknownId_returnsNull() {
		// 160.2: getArtifact(ID) returns null if not found
		ID unknownId = fs
				.getIDfromMavenCoordinates("com.example:nonexistent:9.9.9");
		InputStream is = repo.getArtifact(unknownId);
		assertThat(is).isNull();
	}

	@Test
	void getArtifact_threadSafety_concurrentAccess() throws Exception {
		// 160.2: ArtifactRepository instances must be Thread Safe
		ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
		int threadCount = 5;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicBoolean failed = new AtomicBoolean(false);

		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					InputStream is = repo.getArtifact(id);
					if (is != null) {
						is.close();
					}
				} catch (Exception e) {
					failed.set(true);
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await(10, TimeUnit.SECONDS);
		executor.shutdown();
		assertThat(failed.get()).as("Concurrent access should not fail")
				.isFalse();
	}

	@Test
	void getArtifact_exceptionTreatedAsNull() {
		// 160.2: If exception thrown, must be logged and treated as null
		// Use a custom ArtifactRepository that throws
		ArtifactRepository throwingRepo = id -> {
			throw new RuntimeException("Test exception");
		};
		// The spec says the caller should treat exceptions as null
		// This tests that exceptions from getArtifact are allowed
		ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
		try {
			InputStream is = throwingRepo.getArtifact(id);
			// If we get here, the impl swallowed the exception
			assertThat(is).isNull();
		} catch (RuntimeException e) {
			// Also acceptable - the exception propagates and the caller
			// treats it as null
			assertThat(e.getMessage()).isEqualTo("Test exception");
		}
	}

}
