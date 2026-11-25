package com.artbid.liveshow.domain;

/**
 * 라이브 안에서 작품 한 점의 차례.
 * WAITING: 아직 차례가 오지 않음(입찰 불가). OPEN: 진행자가 열어 입찰 중. DONE: 경매가 마감됨.
 */
public enum LotStatus {
	WAITING, OPEN, DONE
}
