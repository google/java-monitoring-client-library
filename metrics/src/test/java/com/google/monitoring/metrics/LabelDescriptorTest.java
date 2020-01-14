// Copyright 2017 Google LLC. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.monitoring.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LabelDescriptor}. */
@RunWith(JUnit4.class)
public class LabelDescriptorTest {

  @Test
  public void testCreate_invalidLabel_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> LabelDescriptor.create("@", "description"));
    assertThat(thrown).hasMessageThat().contains("Label name must match the regex");
  }

  @Test
  public void testCreate_blankNameField_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> LabelDescriptor.create("", "description"));
    assertThat(thrown).hasMessageThat().contains("Name must not be empty");
  }

  @Test
  public void testCreate_blankDescriptionField_throwsException() {
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> LabelDescriptor.create("name", ""));
    assertThat(thrown).hasMessageThat().contains("Description must not be empty");
  }
}
