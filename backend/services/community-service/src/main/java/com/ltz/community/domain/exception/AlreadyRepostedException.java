package com.ltz.community.domain.exception;

public class AlreadyRepostedException extends RuntimeException {

	public AlreadyRepostedException() {
		super("You have already reposted this post.");
	}
}
