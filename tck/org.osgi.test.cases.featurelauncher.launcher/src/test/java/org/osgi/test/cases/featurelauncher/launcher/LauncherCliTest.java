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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.test.cases.featurelauncher.launcher.LauncherHarness.FeatureBuilder;
import org.osgi.test.cases.featurelauncher.launcher.LauncherHarness.Result;

/**
 * Tests the Feature Launcher through its command line. Section numbers refer to
 * chapter 160, the Feature Launcher Service Specification, and each group of
 * tests is named after the section it tests.
 * <p>
 * Each test that expects a launch to fail first runs a control with the same
 * arguments that must succeed, so the failure can only come from the one thing
 * that differs.
 */
public class LauncherCliTest {

	/** The initial start level of the framework */
	private static final String	BEGINNING	= "org.osgi.framework.startlevel.beginning";

	@TempDir
	Path				dir;

	LauncherHarness		harness;

	@BeforeEach
	void setUp() throws Exception {
		harness = new LauncherHarness(dir);
		harness.installBundle("tck.one");
		harness.installBundle("tck.two");
		harness.installBundle("tck.three");
	}

	Result launch(String featureJson, String... options) throws Exception {
		String[] args = new String[options.length + 2];
		args[0] = "-f";
		args[1] = harness.writeFeature(featureJson).toString();
		System.arraycopy(options, 0, args, 2, options.length);
		return harness.run(args);
	}

	static FeatureBuilder oneBundle() {
		return new FeatureBuilder().bundle("tck.one", null);
	}

	@Nested
	@DisplayName("160.3.2 Overriding Feature variables")
	class FeatureVariables {

		@Test
		@Disabled("The reference launcher ignores -v overrides in the launch properties")
		@DisplayName("160.3.2: an override replaces the default of a variable")
		void variableOverridesAreApplied() throws Exception {
			FeatureBuilder feature = oneBundle()
					.variable("greeting", "\"hello\"")
					.launchProperty("tck.greeting", "\"${greeting}\"");
			String json = feature.json("tck.greeting");

			Result defaulted = launch(json);
			assertThat(defaulted.exit).as(defaulted.toString()).isZero();
			assertThat(defaulted.property("tck.greeting")).isEqualTo("hello");

			Result overridden = launch(json, "-v", "greeting=world");
			assertThat(overridden.exit).as(overridden.toString()).isZero();
			assertThat(overridden.property("tck.greeting")).isEqualTo("world");
		}

		@Test
		@Disabled("The reference launcher does not apply -v to a variable with no default")
		@DisplayName("160.3.2: a variable with no default must be provided")
		void variableWithoutDefaultMustBeProvided() throws Exception {
			FeatureBuilder feature = oneBundle().variable("required", null)
					.launchProperty("tck.value", "\"${required}\"");
			String json = feature.json("tck.value");

			Result provided = launch(json, "-v", "required=given");
			assertThat(provided.exit).as(provided.toString()).isZero();
			assertThat(provided.property("tck.value")).isEqualTo("given");

			Result missing = launch(json);
			assertThat(missing.exit).as(missing.toString()).isNotZero();
		}
	}

	@Nested
	@DisplayName("160.3.3 Setting the bundle start levels")
	class BundleStartLevels {

		@Test
		@DisplayName("160.3.3, 160.4.3.5: bundleStartLevel and minimumStartLevel are applied")
		void bundleStartLevelMetadataAndMinimumStartLevelAreApplied()
				throws Exception {
			FeatureBuilder feature = new FeatureBuilder()
					.bundle("tck.one", "\"bundleStartLevel\":3")
					.launchProperty(BEGINNING, "\"1\"")
					.extension("bundle-start-levels",
							startLevels("\"minimumStartLevel\":3"));
			Result result = launch(feature.json());
			assertThat(result.exit).as(result.toString()).isZero();
			// The bundle only starts if the framework was raised to level 3
			assertThat(result.lines("TCK-START "))
					.containsExactly("tck.one startlevel=3");
		}

		@Test
		@DisplayName("160.3.3: defaultStartLevel applies to bundles without metadata")
		void defaultStartLevelIsAppliedToBundlesWithoutMetadata()
				throws Exception {
			FeatureBuilder feature = new FeatureBuilder()
					.bundle("tck.one", null)
					.launchProperty(BEGINNING, "\"1\"")
					.extension("bundle-start-levels", startLevels(
							"\"defaultStartLevel\":2,\"minimumStartLevel\":2"));
			Result result = launch(feature.json());
			assertThat(result.exit).as(result.toString()).isZero();
			assertThat(result.lines("TCK-START "))
					.containsExactly("tck.one startlevel=2");
		}

		@Test
		@DisplayName("160.3.3: an invalid bundleStartLevel fails the launch")
		void invalidBundleStartLevelFailsTheLaunch() throws Exception {
			// The framework starts at level 3 so a valid level 3 bundle does start
			assertThat(launch(startLevelFeature("3")).exit).isZero();

			for (String bad : Arrays.asList("0", "-1", "\"not-a-number\"")) {
				Result result = launch(startLevelFeature(bad));
				assertThat(result.exit)
						.as("bundleStartLevel " + bad + "\n" + result)
						.isNotZero();
			}
		}

		/** The <code>bundle-start-levels</code> extension with the given members */
		private String startLevels(String members) {
			return "{\"kind\":\"mandatory\",\"type\":\"json\",\"json\":{"
					+ "\"version\":\"1.0.0\"," + members + "}}";
		}

		private String startLevelFeature(String rawStartLevel) {
			return new FeatureBuilder()
					.bundle("tck.one", "\"bundleStartLevel\":" + rawStartLevel)
					.launchProperty(BEGINNING, "\"3\"")
					.json();
		}
	}

	@Nested
	@DisplayName("160.4.2 Using a Feature Launcher")
	class UsingTheLauncher {

		@Test
		@DisplayName("160.4.2: a bundle that cannot be located fails the launch")
		void missingBundleArtifactFailsTheLaunch() throws Exception {
			assertThat(launch(oneBundle().json()).exit).isZero();

			// Same feature, but its bundle is not in any repository
			Result result = launch(new FeatureBuilder()
					.bundle("tck.not.installed", null).json());
			assertThat(result.exit).as(result.toString()).isNotZero();
			assertThat(result.lines("TCK-START ")).isEmpty();
		}
	}

	@Nested
	@DisplayName("160.4.2.1 Providing Framework Launch Properties")
	class FrameworkLaunchProperties {

		@Test
		@DisplayName("160.4.2.1: string, number and boolean values are set as text")
		void featurePropertiesAreSetOnTheFramework() throws Exception {
			FeatureBuilder feature = oneBundle()
					.launchProperty("tck.text", "\"hello\"")
					.launchProperty("tck.number", "1.0")
					.launchProperty("tck.integer", "5000")
					.launchProperty("tck.flag", "true")
					.launchProperty("__tck.underscore", "\"kept\"");
			Result result = launch(feature.json("tck.text", "tck.number",
					"tck.integer", "tck.flag", "_tck.underscore"));
			assertThat(result.exit).as(result.toString()).isZero();
			assertThat(result.property("tck.text")).isEqualTo("hello");
			assertThat(result.property("tck.number")).isEqualTo("1.0");
			assertThat(result.property("tck.integer")).isEqualTo("5000");
			assertThat(result.property("tck.flag")).isEqualTo("true");
			assertThat(result.property("_tck.underscore")).isEqualTo("kept");
		}

		@Test
		@Disabled("The reference launcher accepts a non-scalar framework property")
		@DisplayName("160.4.2.1: a non-scalar value fails the launch")
		void nonScalarFrameworkPropertyFailsTheLaunch() throws Exception {
			assertThat(launch(oneBundle()
					.launchProperty("tck.ok", "\"x\"").json()).exit).isZero();

			Result result = launch(oneBundle()
					.launchProperty("tck.bad", "[\"a\",\"b\"]").json());
			assertThat(result.exit).as(result.toString()).isNotZero();
		}

		@Test
		@Disabled("The reference launcher does not split comma separated pairs for the -l option")
		@DisplayName("160.4.2.1, 160.4.2.4: -l properties override those of the feature")
		void commandLinePropertiesOverrideTheFeature() throws Exception {
			FeatureBuilder feature = oneBundle()
					.launchProperty("tck.text", "\"from-feature\"");
			String json = feature.json("tck.text", "tck.extra");
			Result result = launch(json, "-l",
					"tck.text=from-cli,tck.extra=added");
			assertThat(result.exit).as(result.toString()).isZero();
			assertThat(result.property("tck.text")).isEqualTo("from-cli");
			assertThat(result.property("tck.extra")).isEqualTo("added");
		}
	}

	@Nested
	@DisplayName("160.4.2.4 The Feature Launcher Command Line")
	class CommandLine {

		@Test
		@DisplayName("160.4.2.4: the command waits for the framework to stop and exits 0")
		void launchWaitsForTheFrameworkToStopAndExitsZero() throws Exception {
			Result result = launch(oneBundle().json());
			assertThat(result.exit).as(result.toString()).isZero();
			assertThat(result.lines("TCK-START ")).hasSize(1);
		}

		@Test
		@DisplayName("160.4.2.4: an unreadable feature file is rejected")
		void unreadableFeatureFileIsRejected() throws Exception {
			assertThat(launch(oneBundle().json()).exit).isZero();

			Result result = harness
					.run("-f", dir.resolve("absent.json").toString());
			assertThat(result.exit).as(result.toString()).isNotZero();
		}

		@Test
		@DisplayName("160.4.2.4: -f together with a feature argument is rejected")
		void featureFileAndFeatureArgumentTogetherAreRejected()
				throws Exception {
			String file = harness.writeFeature(oneBundle().json()).toString();
			assertThat(harness.run("-f", file).exit).isZero();

			Result result = harness.run("-f", file, file);
			assertThat(result.exit).as(result.toString()).isNotZero();
			assertThat(result.lines("TCK-START ")).isEmpty();
		}

		@Test
		@DisplayName("160.4.2.4: a value that cannot be converted is rejected")
		void unconvertibleConfigurationValueIsRejected() throws Exception {
			String json = oneBundle().json();
			assertThat(launch(json, "-c", "configuration.timeout=1000").exit)
					.isZero();

			Result result = launch(json,
					"-c", "configuration.timeout=notanumber");
			assertThat(result.exit).as(result.toString()).isNotZero();
		}

		@Test
		@DisplayName("160.4.2.4: a repeated option applies every occurrence")
		void repeatedCommandLineOptionsAreAllApplied() throws Exception {
			String json = oneBundle().json("tck.a", "tck.b");
			Result result = launch(json, "-l", "tck.a=1", "-l", "tck.b=2");
			assertThat(result.exit).as(result.toString()).isZero();
			assertThat(result.property("tck.a")).isEqualTo("1");
			assertThat(result.property("tck.b")).isEqualTo("2");
		}

		@Test
		@Disabled("The reference launcher does not remove the backslash escape (pull request 66)")
		@DisplayName("160.4.2.4: a backslash escapes a comma in a value")
		void escapedCommaIsPartOfTheValue() throws Exception {
			String json = oneBundle().json("tck.list");
			Result result = launch(json, "-l", "tck.list=a\\,b");
			assertThat(result.exit).as(result.toString()).isZero();
			assertThat(result.property("tck.list")).isEqualTo("a,b");
		}
	}

	@Nested
	@DisplayName("160.4.3.4 Installing bundles and configurations")
	class InstallingBundles {

		@Test
		@DisplayName("160.4.3.4: bundles are installed in the order the feature declares them")
		void bundlesAreInstalledAndStartedInDeclaredOrder() throws Exception {
			FeatureBuilder feature = new FeatureBuilder()
					.bundle("tck.three", null).bundle("tck.one", null)
					.bundle("tck.two", null);
			Result result = launch(feature.json());
			assertThat(result.exit).as(result.toString()).isZero();
			assertThat(result.lines("TCK-START "))
					.map(line -> line.substring(0, line.indexOf(' ')))
					.containsExactly("tck.three", "tck.one", "tck.two");
		}
	}
}
