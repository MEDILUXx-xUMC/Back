# 한번만 FE·BE API 계약 초안

Figma의 화면 번호를 기준으로 정리했다. 아래 메서드, 경로, 필드와 상태 코드는 초기 연동 제안이다. 첫 작업 전에 각 기능 담당자가 확인하고, 합의한 변경은 이 문서와 FE 타입·Mock을 함께 갱신한다.

[Figma 와이어프레임](https://www.figma.com/design/VlY2DYbl9vd53axgyePbqR/%ED%95%9C%EB%B2%88%EB%A7%8C?node-id=0-1)

## 1. 공통 HTTP 규약

| 항목 | 초안 |
| --- | --- |
| 기본 경로 | `/api/v1`. 아래 표의 경로는 이 접두사 뒤에 붙인다. |
| 본문과 이름 | JSON, UTF-8. JSON 키와 쿼리는 `camelCase`, enum 값은 대문자와 밑줄을 사용한다. |
| ID | 불투명한 문자열. 클라이언트는 내부 형식을 가정하지 않는다. |
| 금액과 거리 | 금액은 원 단위의 0 이상 정수, 거리는 미터 정수다. 금액은 JS 안전 정수 범위 안에서 주고받는다. |
| 기간과 날짜 | `rentalDays`는 양의 정수, 달력 날짜는 `YYYY-MM-DD`, 시각은 시간대가 있는 ISO 8601 문자열이다. |
| 필드 생략과 null | 요청의 선택 필드 생략은 기본값 적용 또는 변경 없음으로 처리한다. 응답은 표에 명시된 nullable 필드에만 `null`을 사용한다. 부분 수정에서 값을 지울 수 있는 필드는 개별 API에 기재한다. |
| 성공 | 단일 리소스는 JSON 객체, 목록은 `{ "items": [], "nextCursor": null }` 형태다. 조회와 상태 갱신은 200, 리소스 생성은 201을 기본으로 한다. 204에는 본문이 없다. |
| 커서 | `cursor` 생략 시 첫 페이지. 커서는 서버가 발급하고 마지막 페이지에서는 `nextCursor`가 `null`이다. 같은 필터와 정렬에서만 재사용한다. |
| 인증 | 공개라고 적힌 API 외에는 인증된 세션이 필요하다. 인증 전달 방식은 공급자 선정 후 확정한다. 서버는 사용자 ID와 권한을 세션에서 판단한다. |

오류 본문은 `code`, `message`, 선택 필드인 `fieldErrors`를 사용한다. FE는 상태와 `code`로 분기한다. `message`는 표시 가능한 문구이며 `fieldErrors`는 필드 경로별 문구다. [검증 오류 JSON 예시](../contracts/examples/validation-error.json).

| HTTP | code 예시 | 사용 기준 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED`, `INVALID_CURSOR` | 필수값 누락, 타입·범위 오류, 다른 조건으로 발급된 커서 |
| 401 | `AUTH_REQUIRED` | 세션 누락 또는 만료 |
| 403 | `NEIGHBORHOOD_VERIFICATION_REQUIRED`, `POLICY_CONSENT_REQUIRED`, `FORBIDDEN` | 동네 미인증, 동의 누락 또는 역할 권한 부족 |
| 404 | `PRODUCT_NOT_FOUND`, `TRADE_NOT_FOUND`, `CHAT_NOT_FOUND` | 대상 없음. 참여자 전용 리소스는 외부에서도 같은 404를 반환할 수 있다. |
| 409 | `PRODUCT_UNAVAILABLE`, `INVALID_TRADE_STATE`, `IDEMPOTENCY_CONFLICT` | 중복 거래 또는 현재 상태와 충돌 |
| 422 | `PAYMENT_HOLD_FAILED`, `PRODUCT_NOT_ELIGIBLE` | 형식은 맞지만 결제 또는 거래 조건을 만족하지 못함 |
| 413 | `UPLOAD_TOO_LARGE` | 사진 크기 제한 초과 |
| 429 | `RATE_LIMITED` | 일정 시간 동안 요청 횟수 제한 |
| 500/503 | `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE` | 시스템 또는 외부 공급자 장애 |

## 2. 화면과 API 연결

| Figma | 필요한 데이터와 동작 |
| --- | --- |
| 01 예산 선택 | 예산과 기간을 FE 상태로 유지하고 02 조회에 전달 |
| 02 제품 목록 | 예산·동네 범위를 반영한 제품 조회, 빈 목록, 다음 페이지 |
| 03 한번만 요청, T2 요청 확인 | 제품 상세, 기간, 서버 견적, 요청 생성 |
| 04 문고리 수령 | 거래 상태와 문고리 정보 조회, 수령 사진 등록 |
| 05 반납하기 | 반납 기한과 안내 조회, 반납 사진 등록 |
| 06 반납 확인 완료 | 완료·결제 상태와 첫 경험 집계 조회, 후기 작성 |
| O1–O3 서비스 소개, O9 권한 안내, O10 준비 완료 | 정적 안내와 기기 권한은 FE. 서버의 필수 온보딩 단계는 `GET /me`의 상태로 확인 |
| O4 로그인, O5 약관 동의 | 로그인·세션, 정책 버전 조회와 동의 기록 |
| O6 내 동네 설정, O7 동네 인증 | 동네 검색과 현재 위치로 인증 |
| O8 결제수단 등록, O8-1–O8-5 | 정책 조회·동의, PG 등록, 등록 결과 확인 |
| T1 미개봉 생활용품 상세 | 판매 트랙 상세와 견적 |
| T3 주인: 받은 요청, T4 주인: 걸어 두기, T5 주인: 반납 확인 | 받은 요청 조회, 수락·거절, 사진 등록, 반납 확인 또는 이의 제기 |
| T6 내 물건 올리기, T7 미개봉 생활용품 올리기 | 카테고리와 품목 기준, 사진 등록, 트랙별 제품 등록 |
| T8 등록 불가 안내, T9 거래 품목 기준 | 품목 정책 조회, 서버의 등록 거절 사유 표시 |
| M1 마이, M2 내 거래, M3 처음 써 본 것 | 프로필·집계, 참여 거래와 상태, 카테고리별 경험 |
| C1 채팅 목록, C2 채팅방 | 참여 채팅방과 읽지 않은 수, 메시지 조회·전송·읽음 갱신 |

## 3. 제품과 예산

| 메서드·경로 | 요청 | 성공 응답 | 주요 분기 |
| --- | --- | --- | --- |
| GET `/products` | `budgetAmount?`, `rentalDays`, `cursor?`. 예산 생략 시 상한 없는 조회를 제안한다. | `CursorPage<Product>` | 200과 빈 배열, 400, 403 |
| GET `/products/{productId}` | 제품 ID | `ProductDetail` | 403, 404 |
| GET `/categories` | 없음 | `{ items: [{ categoryId, name, allowedTracks }] }` | 각 항목의 활성·품목 기준은 서버 정책을 적용 |
| GET `/me/products` | `cursor?` | `CursorPage<ProductDetail>` | 소유자 본인의 목록 |
| POST `/products` | `track, title, categoryId, description, imageIds`와 아래 트랙별 입력 | 201 `ProductDetail` | 400, 403, 422 |

- `Product` 공통 필드에는 `productId, title, categoryId, thumbnailUrl, neighborhoodName, distanceMeters, isFirstExperience, track`이 있다. `thumbnailUrl`은 사진이 없으면 `null`이다. 상세는 `description, imageUrls, publishedAt`을 더한다. [제품 목록 JSON 예시](../contracts/examples/product-list.json).
- 대여 트랙 `RENTAL`은 `depositAmount, dailyFeeAmount`를 응답한다. 등록의 초기 입력 후보는 같은 가격 필드와 `kcMarked, modified`다. 판매 트랙 `SEALED_SALE`은 `salePriceAmount`를 응답한다. 등록의 초기 입력 후보는 같은 가격 필드와 `sealed, retailOriginal`이다. BE는 서버 품목 기준으로 요청을 검증한다.
- 목록 필터는 대여의 경우 `depositAmount + dailyFeeAmount × rentalDays`, 판매는 `salePriceAmount`를 예산과 비교하는 방안을 제안한다. 결제 단계는 서버 견적으로 재확인한다. 정렬은 처음 써 보는 카테고리 우선, 이후 거리 오름차순과 안정적인 제품 ID 순서를 제안한다. 카테고리별 경험 여부는 현재 사용자 기준이다.
- 목록과 상세는 동네와 거리까지만 반환한다. 문고리 주소와 참가자 전용 사진은 거래 권한에 따라 별도로 조회한다.

## 4. 거래, 문고리, 결제

| 메서드·경로 | 요청 또는 역할 | 성공 응답 |
| --- | --- | --- |
| POST `/trade-quotes` | 요청자. 대여 `{ productId, rentalDays }`, 판매 `{ productId }` | 201 `{ quoteId, track, expiresAt, initialTotalAmount, ...트랙별 금액 }`. 대여는 `depositAmount, usageFeeAmount`, 판매는 `salePriceAmount`. |
| POST `/trades` | 요청자. `{ quoteId, paymentMethodId }`, `Idempotency-Key` | 201 `Trade`. 만료 견적·동시 요청은 409, 보류 실패는 422. |
| GET `/trades` | 참여자. `group?=ACTIVE|CLOSED`, `cursor?` | `CursorPage<Trade>` |
| GET `/trades/{tradeId}` | 참여자 | `Trade` |
| GET `/trades/{tradeId}/handoff` | 수락된 거래의 참여자 | `{ address, instructions, pickupDeadlineAt }`. 수령 기한이 아직 없으면 `null`. |
| GET `/trades/{tradeId}/photos` | 참여자 | `{ items: [{ photoId, purpose, url, createdAt }] }` |
| POST `/trades/{tradeId}/accept` | 소유자. `{ address, instructions? }` | `Trade` |
| POST `/trades/{tradeId}/reject` | 소유자. `{ reason? }` | `Trade` |
| POST `/trades/{tradeId}/ready` | 소유자. `{ handoffPhotoId, conditionPhotoId }` | `Trade` |
| POST `/trades/{tradeId}/pickup` | 요청자. `{ pickupPhotoId }` | `Trade` |
| POST `/trades/{tradeId}/return` | 대여 요청자. `{ returnPhotoId }` | `Trade` |
| POST `/trades/{tradeId}/confirm-return` | 대여 소유자 | `Trade` |
| POST `/trades/{tradeId}/cancel` | 허용 상태의 요청자. `{ reason? }` | `Trade` |
| POST `/trades/{tradeId}/disputes` | 정책상 허용된 참여자. `{ reason, photoIds? }` | 201 `{ disputeId, tradeId, status }` |
| GET `/trades/{tradeId}/payment` | 참여자. 역할에 따라 필요한 정보만 제공 | `{ status, updatedAt }` |
| POST `/trades/{tradeId}/reviews` | 완료 거래의 요청자. `{ text }` | 201 `{ reviewId, tradeId, text, createdAt }` |

`Trade`에는 `tradeId, productId, track, status, currentUserRole, createdAt`이 있다. 역할은 `OWNER` 또는 `REQUESTER`다. 대여는 `rentalDays, returnDueAt, depositAmount, usageFeeAmount`를 추가하고, 기한이 부여되기 전 `returnDueAt`은 `null`이다. 판매는 `salePriceAmount`를 추가한다.

| 트랙·전이 | 동작과 책임 |
| --- | --- |
| 공통 `REQUESTED → ACCEPTED → READY_FOR_PICKUP` | 요청자 제출 → 소유자 수락 → 소유자 사진 등록. 거절·취소 조건은 상태별로 합의한다. |
| 대여 `READY_FOR_PICKUP → IN_USE → RETURN_SUBMITTED → COMPLETED` | 요청자 수령 → 요청자 반납 → 소유자 확인 또는 서버의 기한 처리. |
| 판매 `READY_FOR_PICKUP → COMPLETED` | 수령 증빙을 기준으로 완료하는 초안. 실제 판매 정산 시점은 PG 흐름과 함께 확정한다. |
| 공통 예외 `REJECTED`, `CANCELED`, `DISPUTED` | 서버는 현재 상태와 요청자 역할을 검증하고 허용되지 않은 전이는 409를 반환한다. 분쟁 이후 전이와 보류 처리는 정책 확정 후 추가한다. |

- 04와 T4는 수락 이후 주소와 안내에 접근한다. 완료·취소 등으로 노출 기간이 끝나면 문고리 API가 주소를 반환하지 않는다. 주소는 공용 제품 응답이나 채팅 목록에 포함하지 않는다.
- 거래 상태와 결제 상태를 별도로 조회한다. 결제 상태 후보는 `PENDING`, `HELD`, `RELEASE_PENDING`, `RELEASED`, `CAPTURED`, `FAILED`다. 보류 해제 완료 문구는 PG 결과가 서버에서 확인된 뒤 표시한다.
- 거래 생성과 결제 효과가 있는 동작은 같은 사용자·경로·요청의 재시도에서 결과가 한 번만 반영되도록 한다. 같은 키를 다른 본문에 쓰면 409를 반환한다. BE는 PG 알림도 이벤트 ID로 중복 반영을 막고, 결제 결과와 거래 상태를 다시 확인할 수 있게 한다.
- 와이어프레임 정책에는 수령 30분, 반납 사진 이후 자동 해제 검토 48시간, 연체·분실·이의 제기 기준이 있다. 화면에서는 서버가 계산한 기한을 사용한다. 운영 기한, 예외 처리와 결제 금액 계산은 팀이 적용 정책을 확정한다.

## 5. 온보딩, 파일, 프로필, 채팅

| 화면 | 메서드·경로 | 요청 | 성공 응답과 분기 |
| --- | --- | --- | --- |
| O4 | POST `/auth/session` · DELETE `/auth/session` | 로그인은 공급자 인증 결과. 요청 형식은 공급자 선정과 함께 확정 | 세션 생성 뒤 `GET /me` 조회. 로그아웃 204 |
| O5, O8-1–O8-5, T8–T9 | GET `/policies` · POST `/me/consents` | 정책 목록 및 `{ consents: [{ policyId, version }] }` | 정책 `policyId, version, title, content, required`. 저장 후 동의 버전과 시각 확인 |
| O6–O7 | GET `/neighborhoods?query=...` · POST `/me/neighborhood-verifications` | 검색어 또는 `{ neighborhoodId, latitude, longitude }` | 동네 목록 또는 `{ neighborhoodId, neighborhoodName, verifiedAt, radiusMeters }`. 확인 실패는 422 |
| O8 | POST `/me/payment-method-registration` · GET `/me/payment-methods` | 등록 시작은 PG 연동 방식 확정 후 입력 정의 | PG 등록 세션 정보. 조회는 `paymentMethodId, brand, last4` 등 표시용 필드 |
| 04–05, T4–T7, C2 | POST `/uploads` | `multipart/form-data`의 `file, purpose`와 거래용일 때 `tradeId` | 201 `{ photoId }`. 크기·형식·접근 범위는 용도별로 확인 |
| M1 | GET `/me` · GET `/me/stats` | 본인 세션 | `{ userId, displayName, onboardingStep, neighborhoodName }`, `{ firstExperienceCount, lentCount, earnedUsageFeeAmount }` |
| M2 | GET `/trades` | 그룹과 커서 | 진행 중·완료 그룹 조회 |
| M3 | GET `/me/experiences` | 본인 세션 | `{ items: [{ categoryId, name, firstExperiencedAt }] }`. 미경험은 `null` |
| C1 | GET `/chats` | `cursor?` | `{ items: [{ chatId, tradeId, productTitle, lastMessage, unreadCount }], nextCursor }`. 첫 메시지 전 `lastMessage`는 `null` |
| C2 | GET `/chats/{chatId}/messages` | `cursor?` | 최신순 메시지 페이지. `messageId, senderId, kind, createdAt`과 `TEXT`의 `text` 또는 `PHOTO`의 `photoId, photoUrl` |
| C2 | POST `/chats/{chatId}/messages` | `{ clientMessageId, kind, text }` 또는 `{ clientMessageId, kind, photoId }` | 201 메시지. 같은 발신자의 동일한 `clientMessageId` 재전송은 기존 결과 반환 |
| C2 | PUT `/chats/{chatId}/read` | `{ lastReadMessageId }` | 204. 같은 방에 속한 메시지만 허용 |

결제수단 원본 정보는 PG 등록 흐름을 사용한다. 사용자 API는 표시용 정보와 공급자가 발급한 등록 결과만 다룬다. 업로드된 사진은 업로더, 용도와 거래 참여 권한을 확인한 뒤 연결한다. 채팅은 거래 참여자만 접근하며 실시간 전달 방식은 추후 확정한다.

## 6. 첫 구현 전에 확인할 결정

| 담당 | 확인할 사항 |
| --- | --- |
| FE·BE | 예산 슬라이더 단계와 100만 원 이상 의미, 기본 사용 기간과 최대 기간, 범위 검증과 정렬, 카테고리 ID, 페이지 크기 |
| FE·BE | 인증 공급자와 세션 전달, 동네 조회 공급자와 거리 계산, 인증 만료와 재인증 시점 |
| FE·BE·운영 | 대여와 판매의 결제·정산 시점, 비용·보류 금액, PG 실패·재시도 흐름, 수령·반납·연체·분쟁·취소 정책, 등록 가능 품목 |
| FE·BE | 주소 공개 시점과 사진별 업로드 제한·보관 기간, 채팅 갱신 방식, 푸시 알림 범위 |
| BE | 저장소·DB 마이그레이션 설정, 상태 변경의 동시성 검증, 외부 결제 이벤트 중복 처리 |

제품 목록·상세부터 실제 응답과 JSON 예시, FE 타입과 Mock을 맞춘다. 그다음 요청·수락·수령·반납의 권한과 상태 전이, 온보딩·결제 공급자 연동, 프로필·채팅 순으로 계약을 검토한다. 계약 PR에는 Figma 번호, 영향 화면, 반영 시점과 상대 PR을 기록한다.
