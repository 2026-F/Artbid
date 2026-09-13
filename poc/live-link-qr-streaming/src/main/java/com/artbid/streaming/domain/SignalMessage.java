package com.artbid.streaming.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 방송자(노트북 카메라) <-> 시청자(폰) 간 WebRTC 연결을 맺기 위해 주고받는 시그널링 메시지.
 * 실제 영상 데이터는 이 메시지에 담기지 않는다(브라우저끼리 P2P로 직접 전송) —
 * 서버는 연결을 맺는 데 필요한 SDP/ICE 정보만 role에 따라 중계한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SignalMessage {

    /** 이 메시지를 보낸 쪽. "broadcaster" 또는 "viewer" (서버는 이 값으로 중계 방향만 정한다) */
    private String role;

    /** "join" | "offer" | "answer" | "ice-candidate" */
    private String type;

    /** 메시지가 어느 시청자에 대한 것인지 식별하는 값 (시청자가 접속 시 스스로 생성해서 계속 사용) */
    private String viewerId;

    /** offer / answer 일 때의 SDP */
    private String sdp;

    /** ice-candidate 일 때의 후보 정보 */
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
}
