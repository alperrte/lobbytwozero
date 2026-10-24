package com.ltz.game.api.dto.response;
import java.util.List;
public record FeedbackPageResponse(List<FeedbackResponse> items,int page,int size,long totalElements) { }
