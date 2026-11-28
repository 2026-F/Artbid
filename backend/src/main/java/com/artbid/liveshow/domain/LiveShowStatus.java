package com.artbid.liveshow.domain;

/** 라이브 편성 상태. 끝난(ENDED) 라이브는 다시 시작하지 않는다. */
public enum LiveShowStatus {
	SCHEDULED, LIVE, ENDED
}
