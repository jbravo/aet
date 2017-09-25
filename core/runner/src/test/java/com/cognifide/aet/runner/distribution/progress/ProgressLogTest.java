/**
 * Automated Exploratory Tests
 *
 * Copyright (C) 2013 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognifide.aet.runner.distribution.progress;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * ProgressLogTest
 *
 * @Author: Maciej Laskowski
 * @Date: 04.03.15
 */
public class ProgressLogTest {

  @Test
  public void toString_withNoFailedMessages_expectMessageWithoutFailed() throws Exception {
    ProgressLog progressLog = new ProgressLog("name", 105, 0, 105);
    assertThat(progressLog.toString(), is("name: [success: 105, total: 105]"));
  }

  @Test
  public void toString_withFailedMessages_expectMessageWithFailed() throws Exception {
    ProgressLog progressLog = new ProgressLog("name", 33, 1, 34);
    assertThat(progressLog.toString(), is("name: [success:  33, failed:   1, total:  34]"));
  }
}
