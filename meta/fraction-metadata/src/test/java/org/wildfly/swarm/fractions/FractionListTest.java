/**
 * Copyright 2015-2017 Red Hat, Inc, and individual contributors.
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
package org.wildfly.swarm.fractions;

import org.junit.Test;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Bob McWhirter
 */
public class FractionListTest {

    @Test
    public void testAllHaveLogging() {
        FractionList list = FractionList.get();
        Collection<FractionDescriptor> descriptors = list.getFractionDescriptors();

        for (FractionDescriptor each : descriptors) {
            if ( each.getArtifactId().equals( "container" ) ) {
                // because container cannot have a dependency on logging.
                continue;
            }
            assertThat(hasLogging(each))
                    .as(each.gav() + " has logging")
                    .isTrue();
        }
    }

    private boolean hasLogging(FractionDescriptor desc) {
        if ( desc.getGroupId().equals(FractionDescriptor.THORNTAIL_GROUP_ID) && desc.getArtifactId().equals( "logging" ) ) {
            return true;
        }

        return desc.getDependencies().stream()
                .anyMatch(this::hasLogging);
    }

    @Test
    public void testList() {
        FractionList list = FractionList.get();

        Collection<FractionDescriptor> descriptors = list.getFractionDescriptors();

        FractionDescriptor ee = descriptors.stream().filter(e -> e.getArtifactId().equals("ee")).findFirst().get();

        assertThat(list.getFractionDescriptor(FractionDescriptor.THORNTAIL_GROUP_ID, "ee")).isEqualTo(ee);

        assertThat(ee.getGroupId()).isEqualTo(FractionDescriptor.THORNTAIL_GROUP_ID);
        assertThat(ee.getArtifactId()).isEqualTo("ee");
        assertThat(ee.getDependencies()).hasSize(3);

        assertThat(ee.getDependencies().stream().filter(e -> e.getArtifactId().equals("container")).collect(Collectors.toList())).isNotEmpty();
        assertThat(ee.getDependencies().stream().filter(e -> e.getArtifactId().equals("naming")).collect(Collectors.toList())).isNotEmpty();
    }

    @Test
    public void testMultipleGets() {
        FractionList l1 = FractionList.get();
        FractionList l2 = FractionList.get();
        FractionList l3 = FractionList.get();

        assertThat(l1).isNotNull();
        assertThat(l1).isSameAs(l2);
        assertThat(l2).isSameAs(l3);
    }

    @Test
    public void testGroupIdAndArtifactIdAndNameAndDescriptionAreNeverNull() {
        Collection<FractionDescriptor> descriptors = FractionList.get().getFractionDescriptors();
        assertThat(descriptors).onProperty("groupId").isNotNull();
        assertThat(descriptors).onProperty("artifactId").isNotNull();
        assertThat(descriptors).onProperty("version").isNotNull();
        assertThat(descriptors).onProperty("name").isNotNull();
        assertThat(descriptors).onProperty("description").isNotNull();
    }

    @Test
    public void testNameAndDescription() {
        FractionDescriptor cdi = FractionList.get().getFractionDescriptor(FractionDescriptor.THORNTAIL_GROUP_ID, "jaxrs");
        assertThat(cdi.getName()).isEqualTo("JAX-RS");
        assertThat(cdi.getDescription()).isEqualTo("RESTful Web Services with RESTEasy");
    }

    @Test
    public void testEEFractionDependsOnNamingAndContainer() {
        FractionDescriptor ee = FractionList.get().getFractionDescriptor(FractionDescriptor.THORNTAIL_GROUP_ID, "ee");
        FractionDescriptor naming = FractionList.get().getFractionDescriptor(FractionDescriptor.THORNTAIL_GROUP_ID, "naming");
        FractionDescriptor container = FractionList.get().getFractionDescriptor(FractionDescriptor.THORNTAIL_GROUP_ID, "container");
        Set<FractionDescriptor> dependencies = ee.getDependencies();
        assertThat(dependencies).contains(naming, container);
    }

    @Test
    public void testArchaiusFractionShouldBeInternal() {
        FractionDescriptor archaius = FractionList.get().getFractionDescriptor(FractionDescriptor.THORNTAIL_GROUP_ID, "archaius");
        assertThat(archaius.isInternal()).isTrue();
    }

}
