package org.doit.onbank.admin.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

// FDS AI 보조 판정 (D-72) : TypeSafe Jev 호출. Java SDK 가 없어서 HTTP 로 직접 부른다 (docs.typesafe.ai/sdk)
// POST /v1/systemone + Authorization: Bearer 키. 키는 환경변수 TYPESAFE_API_KEY (설정 파일에 넣지 않음)
// 실패(시간 초과·401·422·429·529 등)는 예외 그대로 올린다 → FdsAiService 가 "AI 판정 없음"으로 바꾼다
@Component
public class JevClient {

    // 0단계 실측한 버전으로 고정. jev-latest 는 새 버전이 나오면 답이 바뀔 수 있다 (models 문서 Aliases)
    public static final String MODEL = "jev-1.13.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final RestClient restClient;
    private final boolean enabled;

    public JevClient(@Value("${TYPESAFE_API_KEY:}") String apiKey) {
        this.enabled = !apiKey.isBlank();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.typesafe.ai")
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    // 키가 없으면(환경변수 미설정) 부르지 않는다
    public boolean isEnabled() {
        return enabled;
    }

    // 질문은 한 번에 같이 보낸다. 서로 독립으로 동시에 판정된다 (primitives "Ask multiple questions together")
    public Response ask(Object state, Map<String, Question> questions) {
        return restClient.post()
                .uri("/v1/systemone")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Request(MODEL, state, questions))
                .retrieve()
                .body(Response.class);
    }

    public record Request(String model, Object state, Map<String, Question> questions) {
    }

    // noul = 예/아니오 확률, choice = 선택지 중 하나. criteria 는 noul 이면 {"true":…, "false":…}, choice 면 {선택지: 설명}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Question(String type, String instructions, Map<String, String> criteria) {

        public static Question noul(String instructions, String whenTrue, String whenFalse) {
            return new Question("noul", instructions, Map.of("true", whenTrue, "false", whenFalse));
        }

        public static Question choice(String instructions, Map<String, String> options) {
            return new Question("choice", instructions, options);
        }
    }

    // model = 실제로 답한 버전, usage(토큰 수)는 안 씀
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String model, Map<String, Answer> answers) {
    }

    // noul 답은 noul 만, choice 답은 choice·confidence·probabilities 만 채워진다 (api/system-one NoulAnswer·ChoiceAnswer)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(String type, Double noul, String choice, Double confidence, Map<String, Double> probabilities) {
    }
}
