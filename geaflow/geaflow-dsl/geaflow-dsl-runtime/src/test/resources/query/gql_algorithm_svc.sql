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

CREATE TABLE circle_result (
  circle_path VARCHAR,
  circle_length INT
) WITH (
  type = 'file',
  geaflow.dsl.file.path = '${target}'
);

-- 使用people图
USE GRAPH people;

-- 调用环路检测算法
INSERT INTO circle_result
CALL single_vertex_circles_detection(
  1,   -- 源顶点ID
  3,   -- 最小环路边数 (3-5)
  5,   -- 最大环路边数
  5    -- 最大环路数量限制
) YIELD (circle_path, circle_length)
RETURN circle_path, circle_length;