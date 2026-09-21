package com.px.base.service;

/**
 * 配桩被拒留痕条目：哪个锚点、因为哪条判定没过。
 */
public record RejectEntry(Long anchorId, String anchorCode, String reason) {
}
