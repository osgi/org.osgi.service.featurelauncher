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

package org.osgi.test.cases.featurelauncher.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Runs the Feature Launcher command line as a separate process against a
 * private Maven repository holding the test bundles and the framework.
 */
final class LauncherHarness {

	static final String	LAUNCHER_PROPERTY			= "featurelauncher.jar";
	static final String	FRAMEWORK_VERSION_PROPERTY	= "framework.version";
	static final String	FRAMEWORK_ID				= "org.apache.felix:org.apache.felix.framework:"
			+ frameworkVersion();

	private static final long	TIMEOUT_SECONDS	= 30;

	private final Path	root;
	private final Path	repo;

	/** The result of running the command */
	static final class Result {
		final int		exit;
		final String	output;

		Result(int exit, String output) {
			this.exit = exit;
			this.output = output;
		}

		/** The values after <code>prefix</code> on the lines starting with it */
		List<String> lines(String prefix) {
			List<String> found = new ArrayList<>();
			for (String line : output.split("\\R")) {
				if (line.startsWith(prefix)) {
					found.add(line.substring(prefix.length()).trim());
				}
			}
			return found;
		}

		/** The reported value of a framework property */
		String property(String key) {
			for (String value : lines("TCK-PROP " + key + "=")) {
				return value;
			}
			return "<not reported>";
		}

		@Override
		public String toString() {
			return "exit " + exit + "\n" + output;
		}
	}

	LauncherHarness(Path root) throws IOException {
		this.root = root;
		this.repo = Files.createDirectories(root.resolve("repo"));
		installFramework();
	}

	/** The version of the framework the Feature runs in, set by the pom */
	static String frameworkVersion() {
		String version = System.getProperty(FRAMEWORK_VERSION_PROPERTY);
		if (version == null || version.isEmpty()) {
			throw new IllegalStateException("Set the system property "
					+ FRAMEWORK_VERSION_PROPERTY
					+ " to the version of the Apache Felix framework on the test class path");
		}
		return version;
	}

	static Path launcherJar() {
		String path = System.getProperty(LAUNCHER_PROPERTY);
		if (path == null || path.isEmpty()
				|| !Files.isRegularFile(Paths.get(path))) {
			throw new IllegalStateException("Set the system property "
					+ LAUNCHER_PROPERTY
					+ " to the executable JAR of the Feature Launcher under test, it was: "
					+ path);
		}
		return Paths.get(path);
	}

	String repositoryUri() {
		return repo.toUri().toString();
	}

	/** Installs a bundle as <code>tck:&lt;name&gt;:1.0.0</code> */
	void installBundle(String name) throws IOException {
		Path dir = Files.createDirectories(repo.resolve("tck/" + name + "/1.0.0"));
		Manifest mf = new Manifest();
		Attributes a = mf.getMainAttributes();
		a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		a.putValue("Bundle-ManifestVersion", "2");
		a.putValue("Bundle-SymbolicName", name);
		a.putValue("Bundle-Version", "1.0.0");
		a.putValue("Bundle-Activator", RecorderActivator.class.getName());
		a.putValue("Import-Package",
				"org.osgi.framework,org.osgi.framework.startlevel");

		String entry = RecorderActivator.class.getName().replace('.', '/')
				+ ".class";
		try (OutputStream out = Files.newOutputStream(dir.resolve(name + "-1.0.0.jar"));
				JarOutputStream jar = new JarOutputStream(out, mf);
				InputStream in = RecorderActivator.class.getClassLoader()
						.getResourceAsStream(entry)) {
			jar.putNextEntry(new ZipEntry(entry));
			in.transferTo(jar);
			jar.closeEntry();
		}
	}

	private void installFramework() throws IOException {
		Class<?> factory;
		try {
			factory = Class.forName("org.apache.felix.framework.FrameworkFactory");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
		CodeSource source = factory.getProtectionDomain().getCodeSource();
		Path jar;
		try {
			jar = Paths.get(source.getLocation().toURI());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		String version = frameworkVersion();
		Path dir = Files.createDirectories(repo
				.resolve("org/apache/felix/org.apache.felix.framework/" + version));
		Files.copy(jar,
				dir.resolve("org.apache.felix.framework-" + version + ".jar"));
	}

	/** Writes a feature file and returns its path */
	Path writeFeature(String json) throws IOException {
		Path file = Files.createTempFile(root, "feature", ".json");
		Files.write(file, json.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	/**
	 * Runs <code>java -jar &lt;launcher&gt; -a &lt;repository&gt; args...</code>
	 * and waits for it to exit
	 */
	Result run(String... args) throws Exception {
		List<String> command = new ArrayList<>();
		command.add(Paths.get(System.getProperty("java.home"), "bin", "java")
				.toString());
		command.add("-jar");
		command.add(launcherJar().toString());
		command.add("-a");
		command.add(repositoryUri());
		for (String arg : args) {
			command.add(arg);
		}

		Path log = Files.createTempFile(root, "launcher", ".log");
		Process process = new ProcessBuilder(command).directory(root.toFile())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
		if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly().waitFor();
			return new Result(-1, "TIMED OUT after " + TIMEOUT_SECONDS
					+ " seconds\n" + new String(Files.readAllBytes(log),
							StandardCharsets.UTF_8));
		}
		return new Result(process.exitValue(),
				new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
	}

	/** A Feature that runs in the framework and records what it sees */
	static final class FeatureBuilder {
		private final List<String>				bundles		= new ArrayList<>();
		private final Map<String,String>		launchProps	= new LinkedHashMap<>();
		private final Map<String,String>		variables	= new LinkedHashMap<>();
		private final List<String>				extensions	= new ArrayList<>();
		private String							stopper;

		/** A bundle with optional extra JSON members of the bundle entry */
		FeatureBuilder bundle(String name, String extraMembers) {
			bundles.add("{\"id\":\"tck:" + name + ":1.0.0\""
					+ (extraMembers == null ? "" : "," + extraMembers) + "}");
			stopper = name;
			return this;
		}

		FeatureBuilder launchProperty(String key, String rawJsonValue) {
			launchProps.put(key, rawJsonValue);
			return this;
		}

		/** A variable, <code>null</code> raw JSON means no default */
		FeatureBuilder variable(String name, String rawJsonValue) {
			variables.put(name, rawJsonValue == null ? "null" : rawJsonValue);
			return this;
		}

		FeatureBuilder extension(String name, String rawJson) {
			extensions.add("\"" + name + "\":" + rawJson);
			return this;
		}

		String json(String... reported) {
			StringBuilder report = new StringBuilder();
			for (String key : reported) {
				report.append(report.length() == 0 ? "" : ",").append(key);
			}
			Map<String,String> props = new LinkedHashMap<>(launchProps);
			if (report.length() > 0) {
				props.put("tck.report", "\"" + report + "\"");
			}
			if (stopper != null) {
				props.put("tck.stopper", "\"" + stopper + "\"");
			}

			StringBuilder json = new StringBuilder(
					"{\"id\":\"tck:feature:1.0.0\",\"complete\":true");
			if (!variables.isEmpty()) {
				json.append(",\"variables\":{");
				join(json, variables);
				json.append("}");
			}
			json.append(",\"bundles\":[").append(String.join(",", bundles))
					.append("]");
			json.append(",\"extensions\":{");
			json.append("\"launch-framework\":{\"kind\":\"mandatory\","
					+ "\"type\":\"artifacts\",\"artifacts\":[{\"id\":\""
					+ FRAMEWORK_ID + "\"}]},");
			json.append("\"framework-launching-properties\":{"
					+ "\"kind\":\"mandatory\",\"type\":\"json\",\"json\":{"
					+ "\"_osgi_featurelauncher_launchprops_version\":\"1.0.0\"");
			for (Map.Entry<String,String> e : props.entrySet()) {
				json.append(",\"").append(e.getKey()).append("\":")
						.append(e.getValue());
			}
			json.append("}}");
			for (String extension : extensions) {
				json.append(",").append(extension);
			}
			json.append("}}");
			return json.toString();
		}

		private static void join(StringBuilder out, Map<String,String> map) {
			boolean first = true;
			for (Map.Entry<String,String> e : map.entrySet()) {
				out.append(first ? "" : ",").append("\"").append(e.getKey())
						.append("\":").append(e.getValue());
				first = false;
			}
		}
	}
}
