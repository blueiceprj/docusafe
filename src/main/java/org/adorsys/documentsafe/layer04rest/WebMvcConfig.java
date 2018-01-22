package org.adorsys.documentsafe.layer04rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.adorsys.documentsafe.layer01persistence.types.BucketName;
import org.adorsys.documentsafe.layer02service.types.DocumentID;
import org.adorsys.documentsafe.layer02service.types.DocumentKeyID;
import org.adorsys.documentsafe.layer02service.types.ReadKeyPassword;
import org.adorsys.documentsafe.layer02service.types.complextypes.DocumentBucketPath;
import org.adorsys.documentsafe.layer03business.types.RelativeBucketPath;
import org.adorsys.documentsafe.layer03business.types.UserID;
import org.adorsys.documentsafe.layer04rest.adapter.BucketNameJsonAdapter;
import org.adorsys.documentsafe.layer04rest.adapter.DocumentBucketPathJsonAdapter;
import org.adorsys.documentsafe.layer04rest.adapter.DocumentIDJsonAdapter;
import org.adorsys.documentsafe.layer04rest.adapter.DocumentKeyIDJsonAdapter;
import org.adorsys.documentsafe.layer04rest.adapter.ReadKeyPasswordJsonAdapter;
import org.adorsys.documentsafe.layer04rest.adapter.RelativeBucketPathJsonAdapter;
import org.adorsys.documentsafe.layer04rest.adapter.UserIDJsonAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.util.List;

/**
 * Created by peter on 13.01.18 at 22:28.
 */
@Configuration
@EnableWebMvc
public class WebMvcConfig extends WebMvcConfigurerAdapter {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(createGsonHttpMessageConverter());
        super.configureMessageConverters(converters);
    }

    private GsonHttpMessageConverter createGsonHttpMessageConverter() {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(DocumentBucketPath.class, new DocumentBucketPathJsonAdapter())
                .registerTypeAdapter(BucketName.class, new BucketNameJsonAdapter())
                .registerTypeAdapter(DocumentID.class, new DocumentIDJsonAdapter())
                .registerTypeAdapter(DocumentKeyID.class, new DocumentKeyIDJsonAdapter())
                .registerTypeAdapter(UserID.class, new UserIDJsonAdapter())
                .registerTypeAdapter(ReadKeyPassword.class, new ReadKeyPasswordJsonAdapter())
                .registerTypeAdapter(RelativeBucketPath.class, new RelativeBucketPathJsonAdapter())
                .create();

        GsonHttpMessageConverter gsonConverter = new GsonHttpMessageConverter();
        gsonConverter.setGson(gson);

        return gsonConverter;
    }

}