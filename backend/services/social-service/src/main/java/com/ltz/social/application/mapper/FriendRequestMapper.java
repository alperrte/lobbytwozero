package com.ltz.social.application.mapper;

import org.springframework.stereotype.Component;

import com.ltz.social.application.service.FriendRequestView;
import com.ltz.social.domain.entity.FriendRequest;

@Component
public class FriendRequestMapper {

    public FriendRequestView toView(FriendRequest request) {
        return new FriendRequestView(request.getId(), request.getSenderId(), request.getReceiverId(),
                request.getStatus(), request.getCreatedAt(), request.getRespondedAt());
    }
}
