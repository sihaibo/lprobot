package com.lp.robot.gate.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;


/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2021-07-11 23:22<br/>
 * @since JDK 1.8
 */
public class HttpUtilManager {

    private String secret;
    private String key;

    private static HttpUtilManager instance = new HttpUtilManager();

    public static HttpUtilManager getInstance() {
        return instance;
    }

    public HttpUtilManager buildKey(String secret, String key) {
        this.secret = secret;
        this.key = key;
        return this;
    }


    private String getString(HttpResponse execute) throws IOException {
        HttpEntity entity = execute.getEntity();
        if (entity == null) {
            return "";
        }
        String responseData;
        try (InputStream is = entity.getContent()) {
            responseData = IOUtils.toString(is, StandardCharsets.UTF_8);
        }
        return responseData;
    }

    public String doRequest(String requestType, String url, Map<String, String> arguments)
            throws IOException {

        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();

        Mac mac = null;
        SecretKeySpec key = null;
        StringBuilder postData = new StringBuilder();

        for (Entry<String, String> argument : arguments.entrySet()) {
            urlParameters.add(new BasicNameValuePair(argument.getKey(), argument.getValue()));
            if (postData.length() > 0) {
                postData.append("&");
            }
            postData.append(argument.getKey()).append("=").append(argument.getValue());
        }
        if (StringUtils.isBlank(this.secret) || StringUtils.isBlank(this.key)) {
            throw new IOException("secret or key is null");
        }

        // Create a new secret key
        key = new SecretKeySpec(this.secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");

        try {
            mac = Mac.getInstance("HmacSHA512");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No such algorithm exception: " + e.toString());
        }

        try {
            assert mac != null;
            mac.init(key);
        } catch (InvalidKeyException ike) {
            System.err.println("Invalid key exception: " + ike.toString());
        }

        // add header
        Header[] headers = new Header[2];
        headers[0] = new BasicHeader("Key", this.key);
        headers[1] = new BasicHeader("Sign",
                Hex.encodeHexString(mac.doFinal(postData.toString().getBytes(StandardCharsets.UTF_8))));

        HttpClient client = HttpClientBuilder.create().build();

        HttpResponse response;
        if (requestType.equalsIgnoreCase("post")) {
            HttpPost post = new HttpPost(url);
            post.setEntity(new UrlEncodedFormEntity(urlParameters));
            post.setHeaders(headers);
            response = client.execute(post);
        } else  {
            HttpGet get = new HttpGet(url);
            get.setHeaders(headers);
            response = client.execute(get);
        }

        return getString(response);
    }

    public String doPostBody(String url, String msg) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity(msg, StandardCharsets.UTF_8));
        return getString(client.execute(post));
    }

    public String doGet(String url) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        return getString(client.execute(new HttpGet(url)));
    }

}