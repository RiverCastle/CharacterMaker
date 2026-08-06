# CaricatureMaker

업로드한 인물 사진을 캐리커처(라인아트) 이미지로 자동 변환해 반환하는 웹 서비스입니다.

- 백엔드: Spring Boot 4 + Java 17
- 프론트엔드: 정적 HTML/CSS/JS (별도 프레임워크 없음)
- 결과 이미지: 1:1 정사각형 고정

## 실행 방법

```bash
./gradlew bootRun
```

기본 포트는 `8081`이며, 실행 후 http://localhost:8081 에서 사용할 수 있습니다.

## 변환 로직 (Provider)

`src/main/resources/application.properties`의 `app.caricature.provider` 값으로 변환 로직을 전환합니다.

| provider 값 | 설명 |
| --- | --- |
| `mock` (기본값) | API 키 없이 동작하는 로컬 이미지 처리(배경 제거 + 엣지 검출) 목업 |
| `gemini` | Google Gemini 이미지 생성 API(`gemini-3.1-flash-image`)를 호출해 실제 AI로 변환 |

## Gemini 모드로 실행하기 (다른 환경에서)

`gemini` 모드는 Google Gemini API 키가 필요합니다. 키는 절대 코드나 `application.properties`에 직접 적지 말고, 아래 두 방법 중 하나로 전달하세요.

### 방법 1. `.env` 파일 사용 (권장)

1. 프로젝트 루트(`CaricatureMaker/`)에 `.env` 파일을 새로 만듭니다. (`.gitignore`에 포함되어 있어 git에는 올라가지 않습니다.)
2. 아래 내용을 입력하고 API 키를 넣습니다.

   ```
   GEMINI_API_KEY=발급받은_API_키
   ```

3. 셸에서 `.env`를 읽어 환경변수로 등록한 뒤 실행합니다.

   **PowerShell**
   ```powershell
   Get-Content ".env" | ForEach-Object {
       if ($_ -match '^\s*([^=#][^=]*)=(.*)$') {
           [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
       }
   }
   ./gradlew bootRun --args="--app.caricature.provider=gemini"
   ```

   **bash**
   ```bash
   set -a; source .env; set +a
   ./gradlew bootRun --args="--app.caricature.provider=gemini"
   ```

### 방법 2. 환경변수를 직접 설정

`.env` 파일 없이 셸에서 바로 환경변수를 설정해도 됩니다.

**PowerShell**
```powershell
$env:GEMINI_API_KEY = "발급받은_API_키"
./gradlew bootRun --args="--app.caricature.provider=gemini"
```

**bash**
```bash
export GEMINI_API_KEY="발급받은_API_키"
./gradlew bootRun --args="--app.caricature.provider=gemini"
```

### 주의사항

- Gemini 이미지 생성 API는 무료 티어를 지원하지 않습니다. API 키가 속한 Google Cloud / AI Studio 프로젝트에 결제(Billing)가 연결되어 있어야 정상 호출됩니다.
- `app.caricature.provider=gemini`를 `application.properties`에 직접 기본값으로 바꿔도 되지만, 그 경우 키가 없는 환경에서는 변환 요청 시 에러가 발생합니다. 키 없이도 항상 동작해야 한다면 기본값 `mock`을 유지하고, 실행 시 `--args`로만 `gemini`를 켜는 걸 권장합니다.

## 이미지 저장 위치

업로드 원본과 변환 결과는 로컬 파일시스템에 저장됩니다 (`application.properties`의 `app.storage.upload-dir`, `app.storage.result-dir`, 기본값은 `storage/uploads`, `storage/results`). 이 폴더는 `.gitignore`에 포함되어 있습니다.
