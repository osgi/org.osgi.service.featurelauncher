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

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.startlevel.BundleStartLevel;

/**
 * The activator of the bundles that the launcher tests install. It reports
 * what the bundle can see on the standard output of the launched process, where
 * the test reads it, and it stops the framework when it is the stopper so the
 * launch command returns.
 * <p>
 * Framework properties control the behaviour: <code>tck.report</code> is a
 * comma separated list of framework properties to report and
 * <code>tck.stopper</code> is the symbolic name of the bundle that stops the
 * framework.
 */
public class RecorderActivator implements BundleActivator {

	@Override
	public void start(BundleContext ctx) {
		Bundle self = ctx.getBundle();
		int level = self.adapt(BundleStartLevel.class).getStartLevel();
		System.out.println(
				"TCK-START " + self.getSymbolicName() + " startlevel=" + level);

		String report = ctx.getProperty("tck.report");
		if (report != null && !report.isEmpty()) {
			for (String key : report.split(",")) {
				System.out.println(
						"TCK-PROP " + key + "=" + ctx.getProperty(key));
			}
		}

		if (self.getSymbolicName().equals(ctx.getProperty("tck.stopper"))) {
			// Stopping inside start() would deadlock the framework
			Thread stopper = new Thread(() -> {
				try {
					ctx.getBundle(0).stop();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}, "tck-stopper");
			stopper.start();
		}
	}

	@Override
	public void stop(BundleContext ctx) {
		// nothing to do
	}
}
