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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;
import org.osgi.service.featurelauncher.repository.ArtifactRepository;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryConstants;
import org.osgi.service.featurelauncher.repository.ArtifactRepositoryFactory;
import org.osgi.test.common.annotation.InjectService;

/**
 * TCK tests for Section 160.2.3: remote Artifact Repositories. Uses a minimal
 * in-process HTTP server backed by a Maven-2 layout of the embedded test
 * bundles, so the {@code http} scheme, retrieval and the authentication
 * properties can be exercised without any external network access.
 */
public class RemoteArtifactRepositoryTest {

	@InjectService
	ArtifactRepositoryFactory	factory;

	@InjectService
	FeatureService				fs;

	private Path				repoDir;
	TestHttpServer				server;

	@BeforeEach
	void setUp() throws Exception {
		repoDir = TckTestHelper.createLocalMavenRepo();
		server = new TestHttpServer(repoDir);
		server.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (server != null) {
			server.stop();
		}
		TckTestHelper.deleteDirectory(repoDir);
	}

	static Map<String,Object> props(Object... kv) {
		return TckTestHelper.mapOf(kv);
	}

	// --- 160.2.3: remote repository basics ---

	@Nested
	class RemoteScheme {

		@Test
		void createRepository_httpScheme_succeeds() throws Exception {
			// 160.2.3: implementations must accept the http scheme
			ArtifactRepository repo = factory.createRepository(
					URI.create(server.baseUri()),
					props(ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_NAME,
							"remote"));
			assertThat(repo).isNotNull();
		}

		@Test
		void getArtifact_overHttp_returnsBytes() throws Exception {
			// 160.2.3: a remote repository retrieves artifacts over http
			ArtifactRepository repo = factory.createRepository(
					URI.create(server.baseUri()),
					props(ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_NAME,
							"remote"));
			ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
			try (InputStream is = repo.getArtifact(id)) {
				assertThat(is).as("artifact must be retrievable over http")
						.isNotNull();
				assertThat(is.read()).as("artifact stream must have content")
						.isNotEqualTo(-1);
			}
		}
	}

	// --- 160.2.3.1: authentication ---

	@Nested
	class Authentication {

		@Test
		void basicAuth_correctCredentials_succeeds() throws Exception {
			server.requireBasicAuth("alice", "secret");
			ArtifactRepository repo = factory.createRepository(
					URI.create(server.baseUri()),
					props(ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_NAME,
							"remote",
							ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_USER,
							"alice",
							ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_PASSWORD,
							"secret"));
			ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
			try (InputStream is = repo.getArtifact(id)) {
				assertThat(is).as(
						"artifact must be retrievable with valid basic auth")
						.isNotNull();
			}
		}

		@Test
		void basicAuth_missingCredentials_returnsNull() throws Exception {
			server.requireBasicAuth("alice", "secret");
			ArtifactRepository repo = factory.createRepository(
					URI.create(server.baseUri()),
					props(ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_NAME,
							"remote"));
			ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
			// 160.2: getArtifact returns null when the artifact cannot be
			// retrieved (here: 401 Unauthorized)
			assertThat(repo.getArtifact(id)).isNull();
		}

		@Test
		void bearerToken_correctToken_succeeds() throws Exception {
			server.requireBearerToken("tok-123");
			ArtifactRepository repo = factory.createRepository(
					URI.create(server.baseUri()),
					props(ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_NAME,
							"remote",
							ArtifactRepositoryConstants.ARTIFACT_REPOSITORY_BEARER_TOKEN,
							"tok-123"));
			ID id = fs.getIDfromMavenCoordinates(TckTestHelper.TB1_COORDS);
			try (InputStream is = repo.getArtifact(id)) {
				assertThat(is).as(
						"artifact must be retrievable with valid bearer token")
						.isNotNull();
			}
		}
	}

	/**
	 * Minimal single-threaded HTTP/1.1 server backed by a directory. Uses only
	 * {@code java.net}/{@code java.io} so it needs no bootdelegation inside the
	 * OSGi framework. Serves GET requests for files below {@code root}; returns
	 * 404 for missing files and 401 when authentication is required but the
	 * request is missing/incorrect.
	 */
	static final class TestHttpServer {
		private final Path				root;
		private ServerSocket			socket;
		private Thread					acceptor;
		private volatile boolean		running;
		private final AtomicReference<String>	requiredAuth	= new AtomicReference<>();

		TestHttpServer(Path root) {
			this.root = root;
		}

		void requireBasicAuth(String user, String password) {
			String token = Base64.getEncoder()
					.encodeToString((user + ":" + password)
							.getBytes(StandardCharsets.UTF_8));
			requiredAuth.set("Basic " + token);
		}

		void requireBearerToken(String token) {
			requiredAuth.set("Bearer " + token);
		}

		void start() throws IOException {
			socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
			running = true;
			acceptor = new Thread(this::acceptLoop, "tck-http-server");
			acceptor.setDaemon(true);
			acceptor.start();
		}

		String baseUri() {
			return "http://" + socket.getInetAddress().getHostAddress() + ":"
					+ socket.getLocalPort() + "/";
		}

		void stop() {
			running = false;
			try {
				socket.close();
			} catch (IOException e) {
				// ignore
			}
		}

		private void acceptLoop() {
			while (running) {
				try {
					Socket client = socket.accept();
					handle(client);
				} catch (IOException e) {
					// socket closed on stop(), or transient - keep going
				}
			}
		}

		private void handle(Socket client) {
			try (Socket c = client) {
				PushbackInputStream in = new PushbackInputStream(
						c.getInputStream());
				String requestLine = readLine(in);
				if (requestLine == null || requestLine.isEmpty()) {
					return;
				}
				String[] parts = requestLine.split(" ");
				String target = parts.length > 1 ? parts[1] : "/";
				String authHeader = null;
				String header;
				while ((header = readLine(in)) != null && !header.isEmpty()) {
					int colon = header.indexOf(':');
					if (colon > 0 && header.substring(0, colon).trim()
							.equalsIgnoreCase("Authorization")) {
						authHeader = header.substring(colon + 1).trim();
					}
				}
				OutputStream out = c.getOutputStream();
				String expected = requiredAuth.get();
				if (expected != null && !expected.equals(authHeader)) {
					// Include a challenge so a non-preemptive HTTP client knows
					// to retry with credentials.
					String scheme = expected.startsWith("Bearer") ? "Bearer"
							: "Basic realm=\"tck\"";
					out.write(("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: "
							+ scheme
							+ "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
									.getBytes(StandardCharsets.UTF_8));
					out.flush();
					return;
				}
				Path file = root.resolve(target.replaceFirst("^/", ""))
						.normalize();
				if (file.startsWith(root) && Files.isRegularFile(file)) {
					byte[] body = Files.readAllBytes(file);
					out.write(("HTTP/1.1 200 OK\r\nContent-Length: "
							+ body.length
							+ "\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n")
									.getBytes(StandardCharsets.UTF_8));
					out.write(body);
				} else {
					writeStatus(out, 404, "Not Found");
				}
				out.flush();
			} catch (IOException e) {
				// best effort
			}
		}

		private static void writeStatus(OutputStream out, int code, String msg)
				throws IOException {
			out.write(("HTTP/1.1 " + code + " " + msg
					+ "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
							.getBytes(StandardCharsets.UTF_8));
		}

		private static String readLine(InputStream in) throws IOException {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			int b;
			boolean any = false;
			while ((b = in.read()) != -1) {
				any = true;
				if (b == '\r') {
					continue;
				}
				if (b == '\n') {
					break;
				}
				buf.write(b);
			}
			if (!any) {
				return null;
			}
			return new String(buf.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}
