# 한번만 백엔드

동네 이웃 간 전자제품 대여와 미개봉 생활용품 거래를 지원하는 REST API다. 제품 탐색부터 문고리 수령·반납까지 두 개발 계정으로 확인할 수 있다.

**기술 스택** · Java 21, Spring Boot 3.5.16, Gradle Wrapper 8.14.3, PostgreSQL 17, Flyway

## 로컬 실행

JDK 21과 Docker Compose가 필요하다. Docker를 실행한 뒤 Back 저장소 루트에서 아래 명령을 실행한다.

```powershell
docker compose up -d --wait
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

macOS와 Linux에서는 두 번째 명령으로 `./gradlew bootRun --args='--spring.profiles.active=local'`을 사용한다.

- API 기본 주소는 `http://127.0.0.1:8080/api/v1`이다.
- PostgreSQL은 `127.0.0.1:5432`에서 실행된다. 서버가 시작될 때 Flyway가 스키마와 로컬 예시 데이터를 적용한다.
- 로컬 CORS는 `http://localhost:5173`과 `http://127.0.0.1:5173`을 허용한다.
- 서버를 종료하려면 터미널에서 `Ctrl+C`를 누른다. `docker compose stop`으로 DB를 중지하면 데이터 볼륨은 유지된다.

## 첫 API 호출

```powershell
$api = "http://127.0.0.1:8080/api/v1"

# 예산 49,000원으로 이틀간 이용할 수 있는 제품 조회
Invoke-RestMethod "$api/products?rentalDays=2&budgetAmount=49000"

# 빌리는 이웃으로 로그인하고 프로필 조회
$login = Invoke-RestMethod "$api/auth/session" -Method Post -ContentType "application/json" -Body '{"userId":"demo-borrower"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Invoke-RestMethod "$api/me" -Headers $headers
```

로컬 제품 목록·상세 조회는 토큰을 생략하면 `demo-borrower`로 동작한다. 거래를 확인할 때는 빌리는 이웃 `demo-borrower`와 주인 `demo-owner`의 토큰을 각각 발급받는다. 토큰은 8시간 유효하다. [두 계정으로 대여·판매 확인하기](docs/LOCAL_API.md)

## 구현된 기능

경로의 공통 접두사는 `/api/v1`이다.

| 기능 | 대표 API | 동작 |
| --- | --- | --- |
| 제품과 카테고리 | `GET /products`, `GET /products/{productId}`, `POST /products`, `GET /categories`, `GET /me/products` | 예산·사용 일수·동네 반경 조회, 커서 페이지 이동, 트랙별 품목 검증과 등록 |
| 온보딩 | `POST /auth/session`, `GET /me`, `GET /policies`, `POST /me/consents`, `POST /me/neighborhood-verifications` | 개발 세션, 약관 동의, 좌표 기반 동네 확인, 다음 단계 조회 |
| 제품·거래 사진 | `POST /uploads`, `GET /uploads/{id}` | PNG·JPEG를 파일당 5MB까지 저장하고 거래 사진의 참여자 권한 확인 |
| 대여와 판매 | `POST /trade-quotes`, `POST /trades`, `GET /trades`, `GET /trades/{id}` | 견적, 중복 요청 처리, 수락·거절, 사진 등록, 수령·반납 확인, 취소·분쟁 접수·후기 |
| 결제 | `GET /me/payment-methods`, `POST /me/payment-method-registration`, `GET /trades/{id}/payment` | 로컬 결제수단과 모의 보류·해제·결제 결과 |
| 마이와 채팅 | `GET /me/stats`, `GET /me/experiences`, `GET /chats`, `GET/POST /chats/{id}/messages`, `PUT /chats/{id}/read` | 통계·첫 경험, 거래 역할별 채팅 목록, 텍스트·사진 전송과 읽음 처리 |

[화면별 API 계약과 요청·응답 필드](docs/API_CONTRACT.md)

## 코드 구조

```text
src/main/java/com/hanbeonman/
├── catalog/      제품 조회·등록, 카테고리
├── identity/     세션, 정책 동의, 동네 인증
├── trade/        견적, 거래 상태 전이, 참여 권한
├── upload/       이미지 저장·조회, 용도 검증
├── payment/      로컬 결제수단 등록과 조회
├── profile/      이용 통계와 첫 경험
├── chat/         거래별 메시지와 읽음 위치
└── common/       HTTP 오류, 입력 검증, CORS

src/main/resources/
├── application.yaml        공통 설정
├── application-local.yaml  로컬 서버와 DB 설정
└── db/
    ├── migration/          공통 스키마·정책 마이그레이션
    └── local/              로컬 개발용 사용자·제품·결제수단
```

## 환경 설정과 연동 범위

| 환경 변수 | 역할 | `local` 기본값 |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC 주소 | `jdbc:postgresql://127.0.0.1:5432/hanbeonman` |
| `DB_USERNAME` | DB 사용자 | `hanbeonman` |
| `DB_PASSWORD` | DB 비밀번호 | Compose와 동일한 로컬 개발용 값 |
| `SERVER_PORT` | API 포트 | `8080` |

기본 프로필에서는 DB 연결 값 세 개를 실행 환경에서 주입한다. 개발 계정 로그인과 모의 결제 처리는 `local` 프로필에서 제공한다. 동네 확인은 입력 좌표와 동네 기준점의 거리를 계산하고, 사진은 PostgreSQL에 저장한다. 채팅은 HTTP로 조회한다. 실제 인증·지도·PG·파일 저장소 및 운영용 연체·분쟁 처리 기준은 후속 연동 범위다.

## 빌드와 협업

```powershell
.\gradlew.bat build
```

macOS와 Linux에서는 `./gradlew build`를 실행한다. API 변경 시 동일 조건의 성공·오류 응답을 로컬 서버에서 확인한다.

- [BE 협업 규칙](docs/CONTRIBUTING.md)
- [프로젝트 구조와 연동 순서](docs/PROJECT_SETUP.md)
- [백엔드 구현 계획](docs/IMPLEMENTATION_PLAN.md)
