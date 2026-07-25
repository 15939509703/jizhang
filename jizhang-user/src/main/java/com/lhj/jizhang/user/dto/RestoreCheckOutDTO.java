package com.lhj.jizhang.user.dto;
public record RestoreCheckOutDTO(Long id, Long backupId, Integer addedCount, Integer skippedCount,
                                 Integer conflictCount, String status) { }
