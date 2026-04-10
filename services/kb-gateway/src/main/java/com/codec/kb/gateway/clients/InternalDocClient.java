package com.codec.kb.gateway.clients;

import com.codec.kb.gateway.GatewayConfig;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public final class InternalDocClient {
  private static final String INTERNAL_HEADER = "X-Internal-Token";

  private final GatewayConfig cfg;
  private final RestClient rc;

  public InternalDocClient(GatewayConfig cfg, RestClient rc) {
    this.cfg = cfg;
    this.rc = rc;
  }

  public Object uploadToKb(String kbId, MultipartFile file, String category, String tags) throws IOException {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart(file));
    if (category != null) body.add("category", category);
    if (tags != null) body.add("tags", tags);

    try {
      return rc.post()
          .uri(cfg.docUrl() + "/internal/kbs/" + kbId + "/documents/upload")
          .header(INTERNAL_HEADER, cfg.internalToken())
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(body)
          .retrieve()
          .body(Object.class);
    } catch (RestClientResponseException e) {
      throw DownstreamClientSupport.translate(e, "文档上传失败");
    } catch (RestClientException e) {
      throw DownstreamClientSupport.translate(e, "文档服务不可用");
    }
  }

  private static HttpEntity<InputStreamResource> filePart(MultipartFile file) throws IOException {
    InputStreamResource resource = new InputStreamResource(file.getInputStream()) {
      @Override
      public String getFilename() {
        return file.getOriginalFilename();
      }

      @Override
      public long contentLength() {
        return file.getSize();
      }
    };

    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    return new HttpEntity<>(resource, fileHeaders);
  }
}
