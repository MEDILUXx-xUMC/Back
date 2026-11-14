# 로컬 API 검증

[README](../README.md)의 명령으로 서버를 실행하고 `http://127.0.0.1:8080/api/v1`에 요청한다.

## 로그인과 테스트 결제수단

1. `POST /auth/session`에 `{"userId":"demo-borrower"}` 또는 `{"userId":"demo-owner"}`를 보낸다.
2. 응답의 `accessToken`을 다음 요청의 `Authorization: Bearer <accessToken>` 헤더에 넣는다. 토큰은 8시간 유효하다. `DELETE /auth/session`으로 폐기할 수 있다.
3. `GET /policies`의 필수 정책 ID와 버전을 `POST /me/consents`에 보내고 `GET /me`에서 다음 단계를 확인한다. 인증 예시 좌표는 사우동 `sau`의 위도 `37.62`, 경도 `126.719`다.
4. `GET /me/payment-methods`에서 예시 결제수단의 ID를 얻는다. `POST /me/payment-method-registration`은 로컬 카드 한 개를 추가한다.

## 대여 검증 순서

| 순서 | 사용자 | 요청 |
| --- | --- | --- |
| 1 | 빌리는 이웃 | `POST /trade-quotes`에 `{"productId":"product-keyboard","rentalDays":2}` 전달 |
| 2 | 빌리는 이웃 | `POST /trades`에 견적 ID, 결제수단 ID, 고유한 `Idempotency-Key` 헤더 전달 |
| 3 | 주인 | `POST /trades/{tradeId}/accept`에 문고리 `address` 전달 |
| 4 | 주인 | `HANDOFF`·`CONDITION` 사진을 각각 올리고 `/ready`에 두 사진 ID 전달 |
| 5 | 빌리는 이웃 | `PICKUP` 사진을 올리고 `/pickup`에 전달 |
| 6 | 빌리는 이웃 | `RETURN` 사진을 올리고 `/return`에 전달 |
| 7 | 주인 | `/confirm-return`으로 완료하고 `/payment`에서 모의 보류 해제를 확인 |

사진은 `POST /uploads`로 `file`, `purpose`, 거래 사진이면 `tradeId`를 multipart로 전송한다. PNG와 JPEG를 파일당 5MB까지 받는다. 제품 등록용 사진은 `purpose=PRODUCT`로 올린 ID를 `imageIds`에 전달한다.

미개봉 판매는 `product-softener`의 견적을 요청할 때 `rentalDays`를 생략한다. 판매는 수령 확인에서 완료된다. 거래 ID로 `GET /chats/{tradeId}/messages`를 호출할 수 있다. 내 거래와 채팅의 `role`은 `OWNER` 또는 `REQUESTER`로 조회한다.

## 개발 환경의 범위

- 인증은 두 개발 계정의 Bearer 세션이다. 좌표는 클라이언트 입력을 받아 동네 기준점의 1km 이내인지 계산한다.
- 결제 보류·해제와 판매 결제는 `local` 프로필의 DB 상태로 재현한다. 수령 기한은 30분이고 반납 증빙을 올린 뒤 48시간 동안 분쟁이 없으면 로컬 작업에서 자동 확인한다.
- 거절과 요청자 취소는 요청 단계에서 가능하다. 분쟁 접수는 거래를 중단하며 운영자 판정과 실제 결제 조정은 공급자 연동 범위다.
- 사진은 PostgreSQL에 저장한다. 문고리 주소와 거래 사진은 참여자 권한을 확인한다. 채팅은 HTTP 조회 방식이다.
- 운영 적용 전 실제 인증·지도·PG·파일 저장소 및 승인된 정책 문구, 연체·분쟁 처리 기준이 필요하다.
