/*
 * Copyright 2026-Present The Case Hub Authors
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
package io.casehub.blocks.agentic.judgment;

/**
 * SPI for inline judgment evaluation within agentic patterns. Called by the execution loop after
 * aggregation to decide whether the iteration result should be accepted, rejected (re-iterate), or
 * escalated.
 *
 * <p>Refs blocks#219, engine#994.
 */
@FunctionalInterface
public interface JudgmentPhase<T> {
  JudgmentDecision evaluate(JudgmentContext<T> context);
}
