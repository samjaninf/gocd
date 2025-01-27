/*
 * Copyright Thoughtworks, Inc.
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
 */
package com.thoughtworks.go.server.materials;

import com.thoughtworks.go.config.materials.mercurial.HgMaterial;
import com.thoughtworks.go.helper.MaterialsMother;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MaterialUpdateFailedMessageTest {

    @Test
    public void shouldReturnTheExceptionMessageAsReasonForFailure() {
        assertThat(new MaterialUpdateFailedMessage(MaterialsMother.hgMaterial(), 1, new RuntimeException("Message")).getReason()).isEqualTo("Message");
    }

    @Test
    public void shouldReturnEmptyStringIfThereIsNotMessage() {
        assertThat(new MaterialUpdateFailedMessage(MaterialsMother.hgMaterial(), 2, new NullPointerException()).getReason()).isEqualTo("");
    }

    @Test
    public void shouldMessageAndTheCauseIfOneExists() {
        assertThat(new MaterialUpdateFailedMessage(MaterialsMother.hgMaterial(), 3, new Exception("Message", new RuntimeException("Foo"))).getReason()).isEqualTo("Message. Cause: Foo");
    }

    @Test
    public void shouldNotRepeatMessageAndCauseIfTheContentAreTheSame() {
        MaterialUpdateFailedMessage message = new MaterialUpdateFailedMessage(mock(HgMaterial.class), 4, new RuntimeException("some messAGE", new RuntimeException("some messAGE")));
        assertThat(message.getReason()).isEqualTo("some messAGE");
    }
}
