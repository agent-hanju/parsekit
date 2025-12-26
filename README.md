# ParseKit

문서 파싱 및 변환 서비스를 위한 Java 클라이언트 라이브러리

## 기능

- **문서 파싱** - Docling을 통해 문서(PDF, DOCX, PPTX, XLSX, HWP)를 Markdown으로 변환
- **OCR** - VLM 또는 Docling을 사용해 이미지에서 텍스트 추출
- **포맷 변환** - LibreOffice를 통해 문서를 PDF 또는 이미지로 변환

## 요구사항

- Java 21+
- Spring WebFlux

## 설치

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.agent-hanju:parsekit:0.1.1'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.agent-hanju</groupId>
    <artifactId>parsekit</artifactId>
    <version>0.1.1</version>
</dependency>
```

## 사용법

### 문서 파싱

```java
DoclingClient client = new DoclingClient(WebClient.builder(), "http://localhost:5000");
ParseResult result = client.parse(fileBytes, "document.pdf");
String markdown = result.asMarkdown();
```

### OCR

```java
VlmClient client = new VlmClient(WebClient.builder(), "http://localhost:8000", "Qwen/Qwen2-VL-7B-Instruct");
String text = client.ocr(imageBytes);
```

### 문서 변환

```java
ConverterClient client = new ConverterClient(WebClient.builder(), "http://localhost:3000");
ConvertResult result = client.convert(docxBytes, "document.docx", null);
byte[] pdf = result.content();
```

## 관련 프로젝트

- [parsekit-converter](https://github.com/agent-hanju/parsekit-converter) - LibreOffice 기반 문서 변환 서버
