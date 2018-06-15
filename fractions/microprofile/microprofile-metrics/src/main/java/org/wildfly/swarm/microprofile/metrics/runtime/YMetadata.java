/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.microprofile.metrics.runtime;

import java.util.List;
import java.util.Map;

/**
 * @author hrupp
 */
@SuppressWarnings("unused")
public class YMetadata {



  String name;
  String displayName;
  String description;
  boolean multi;
  boolean reusable;
  String mbean;
  String unit;
  String type;
  List<Map<String,String>> labels;


  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Boolean getMulti() {
    return multi;
  }

  public void setMulti(Boolean multi) {
    this.multi = multi;
  }

  public String getMbean() {
    return mbean;
  }

  public void setMbean(String mbean) {
    this.mbean = mbean;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public List<Map<String, String>> getLabels() {
    return labels;
  }

  public void setLabels(List<Map<String, String>> labels) {
    this.labels = labels;
  }

  public boolean isMulti() {
    return multi;
  }

  public void setMulti(boolean multi) {
    this.multi = multi;
  }

  public boolean isReusable() {
    return reusable;
  }

  public void setReusable(boolean reusable) {
    this.reusable = reusable;
  }
}
