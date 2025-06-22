/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

-- 顶点表定义
CREATE TABLE v_person (
  name varchar,
  age int,
  id bigint
) WITH (
  type = 'file',
  geaflow.dsl.file.path = 'resource:///data/v_person.txt'
);

-- 边表定义
CREATE TABLE e_knows (
  srcId bigint,
  targetId bigint,
  weight double
) WITH (
  type = 'file',
  geaflow.dsl.file.path = 'resource:///data/e_knows.txt'
);

-- 图定义 - 完全遵循modern_graph.sql的语法
CREATE GRAPH people (
  Vertex person USING v_person WITH ID(id),  
  Edge knows USING e_knows WITH ID(srcId, targetId) 
) WITH (
  storeType = 'rocksdb',
  shardCount = 2
);