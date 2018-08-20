package com.intuit.developer.helloworld.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vendhq.api.ApiClient;
import com.vendhq.api.ApiException;
import com.vendhq.api.CustomersApi;
import com.vendhq.api.auth.ApiKeyAuth;
import com.vendhq.models.CustomerCollection;

@Configuration
public class VendHQConfiguration {

	@Value("${vendhq.domainprefix}")
	private String domainPrefix;
	
	@Value("${vendhq.applicationName}")
	private String applicationName;
	
	@Value("${vendhq.personal.access_token}")
	private String personalAccessToken;
	
	@Bean()
	public ApiClient configureApiClient() {
		com.vendhq.api.Configuration configuration = new com.vendhq.api.Configuration(domainPrefix, applicationName);
		ApiClient apiClient =  configuration.getDefaultApiClient();
//		ApiKeyAuth personal_token = (ApiKeyAuth) apiClient.getAuthentication("personal_token");
//		personal_token.setApiKey(personalAccessToken);
//		personal_token.setApiKeyPrefix("Bearer");
		return apiClient;
		
	}
	
	@Bean()
	public ApiKeyAuth configureApiAuthKey(ApiClient apiClient) {
		ApiKeyAuth personal_token = (ApiKeyAuth) apiClient.getAuthentication("personal_token");
		personal_token.setApiKey(personalAccessToken);
		personal_token.setApiKeyPrefix("Bearer");
		
		//Retrieve Customers
		CustomersApi apiInstance = new CustomersApi(apiClient);
		Long after = 789L; // Long | The lower limit for the version numbers to be included in the response.
		Long before = 789L; // Long | The upper limit for the version numbers to be included in the response.
		Integer pageSize = 56; // Integer | The maximum number of items to be returned in the response.
		try {
		    CustomerCollection result = apiInstance.listCustomers(after, before, pageSize);
		    System.out.println(result);
		} catch (ApiException e) {
		    System.err.println("Exception when calling CustomersApi#listCustomers");
		    e.printStackTrace();
		}
		
		return personal_token;
	}
	
	
}
