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

package com.antgroup.geaflow.dsl.udf.graph;

import com.antgroup.geaflow.common.type.primitive.IntegerType;
import com.antgroup.geaflow.common.type.primitive.StringType;
import com.antgroup.geaflow.dsl.common.algo.AlgorithmRuntimeContext;
import com.antgroup.geaflow.dsl.common.algo.AlgorithmUserFunction;
import com.antgroup.geaflow.dsl.common.data.Row;
import com.antgroup.geaflow.dsl.common.data.RowEdge;
import com.antgroup.geaflow.dsl.common.data.RowVertex;
import com.antgroup.geaflow.dsl.common.data.impl.ObjectRow;
import com.antgroup.geaflow.dsl.common.function.Description;
import com.antgroup.geaflow.dsl.common.types.GraphSchema;
import com.antgroup.geaflow.dsl.common.types.StructType;
import com.antgroup.geaflow.dsl.common.types.TableField;
import com.antgroup.geaflow.model.graph.edge.EdgeDirection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Description(
    name = "single_vertex_circles_detection",
    description = "Detects circles starting and ending at a specified vertex."
)
public class SingleVertexCirclesDetection implements AlgorithmUserFunction<Long, List<Long>> {

    private AlgorithmRuntimeContext<Long, List<Long>> context;
    private long sourceId;
    private int minCircleEdges;
    private int maxCircleEdges;
    private int maxCircleCount;
    private final Set<String> foundCircles = new HashSet<>();
    private int circleCount = 0;

    @Override
    public void init(AlgorithmRuntimeContext<Long, List<Long>> context, Object[] parameters) {
        this.context = context;
        
        // 参数必须包含源顶点ID
        if (parameters == null || parameters.length < 1 || !(parameters[0] instanceof Number)) {
            throw new IllegalArgumentException("Source vertex ID (Long) is required");
        }
        this.sourceId = ((Number) parameters[0]).longValue();
        
        // 设置默认值
        this.minCircleEdges = 3;
        this.maxCircleEdges = 5;
        this.maxCircleCount = 5;
        
        // 处理可选参数
        if (parameters.length > 1 && parameters[1] instanceof Number) {
            this.minCircleEdges = ((Number) parameters[1]).intValue();
        }
        if (parameters.length > 2 && parameters[2] instanceof Number) {
            this.maxCircleEdges = ((Number) parameters[2]).intValue();
        }
        if (parameters.length > 3 && parameters[3] instanceof Number) {
            this.maxCircleCount = ((Number) parameters[3]).intValue();
        }
        
        // 严格的参数验证
        if (minCircleEdges < 3) {
            throw new IllegalArgumentException("Min circle edges must be at least 3");
        }
        if (maxCircleEdges < minCircleEdges) {
            throw new IllegalArgumentException("Max circle edges must be >= min circle edges");
        }
        if (maxCircleCount <= 0) {
            throw new IllegalArgumentException("Max circle count must be positive");
        }
    }

    @Override
    public void process(RowVertex vertex, Optional<Row> updatedValues, Iterator<List<Long>> messages) {
        // 如果达到环数限制则直接返回
        if (circleCount >= maxCircleCount) {
            return;
        }
        
        long currentId = getVertexId(vertex);
        if (currentId == -1L) return;
        
        long iteration = context.getCurrentIterationId();
        
        // 处理源顶点（只处理一次）
        if (iteration == 1L && currentId == sourceId) {
            startFromSource();
        } 
        // 处理后续迭代
        else if (iteration > 1 && iteration <= maxCircleEdges) {
            while (messages.hasNext()) {
                processMessage(currentId, messages.next());
                if (circleCount >= maxCircleCount) break;
            }
        }
    }

    private long getVertexId(RowVertex vertex) {
        try {
            Object id = vertex.getId();
            if (id instanceof Long) return (Long) id;
            if (id instanceof Number) return ((Number) id).longValue();
            return -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private void startFromSource() {
        List<RowEdge> outEdges = context.loadEdges(EdgeDirection.OUT);
        if (outEdges == null || outEdges.isEmpty()) return;
        
        // 只处理直接邻居
        for (RowEdge edge : outEdges) {
            Object targetIdObj = edge.getTargetId();
            if (!(targetIdObj instanceof Number)) continue;
            
            long targetId = ((Number) targetIdObj).longValue();
            if (targetId == sourceId) continue; // 跳过自环
            
            // 创建并发送初始路径
            sendInitialPath(targetId);
            
            // 达到环数限制则停止
            if (circleCount >= maxCircleCount) return;
        }
    }

    private void sendInitialPath(long targetId) {
        // 初始路径包含两个节点：源点和目标点
        List<Long> path = new ArrayList<>(2);
        path.add(sourceId);
        path.add(targetId);
        context.sendMessage(targetId, path);
    }

    private void processMessage(long currentId, List<Long> path) {
        if (path == null || path.isEmpty()) return;
        
        // 检测是否形成环路
        if (currentId == sourceId) {
            int edgeCount = path.size(); // 路径节点数 = 边数 + 1
            if (edgeCount >= minCircleEdges + 1 && edgeCount <= maxCircleEdges + 1) {
                recordCircle(path);
            }
            return;
        }
        
        // 检查是否可以扩展路径
        if (path.size() < maxCircleEdges + 1) { // 节点数 < 最大边数 + 1
            if (!path.contains(currentId)) {
                extendPath(currentId, path);
            }
        }
    }

    private void extendPath(long currentId, List<Long> path) {
        List<RowEdge> outEdges = context.loadEdges(EdgeDirection.OUT);
        if (outEdges == null || outEdges.isEmpty()) return;
        
        // 创建新路径（包含当前节点）
        List<Long> newPath = new ArrayList<>(path.size() + 1);
        newPath.addAll(path);
        newPath.add(currentId);
        
        // 只发送消息给未被访问的邻居节点
        for (RowEdge edge : outEdges) {
            Object targetIdObj = edge.getTargetId();
            if (!(targetIdObj instanceof Number)) continue;
            
            long targetId = ((Number) targetIdObj).longValue();
            
            // 跳过已在路径中的节点（源节点除外）
            if (newPath.contains(targetId) && targetId != sourceId) continue;
            
            // 发送消息
            context.sendMessage(targetId, newPath);
            
            // 达到环数限制则停止
            if (circleCount >= maxCircleCount) return;
        }
    }

    private void recordCircle(List<Long> path) {
        // 生成环的唯一标识（排序后节点列表）
        List<Long> sortedPath = new ArrayList<>(path);
        Collections.sort(sortedPath);
        String circleId = sortedPath.toString();
        
        // 确保是未发现的环
        if (!foundCircles.contains(circleId)) {
            foundCircles.add(circleId);
            circleCount++;
            
            // 环的边数 = 节点数 - 1
            int circleEdges = path.size() - 1;
            context.take(ObjectRow.create(path.toString(), circleEdges));
        }
    }

    @Override
    public void finish(RowVertex vertex, Optional<Row> updatedValues) {
        // 清理资源
        foundCircles.clear();
    }

    @Override
    public StructType getOutputType(GraphSchema graphSchema) {
        return new StructType(
            new TableField("circle_path", StringType.INSTANCE, false),
            new TableField("circle_length", IntegerType.INSTANCE, false)
        );
    }
}