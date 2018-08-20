/**
 * 
 */
package com.intuit.developer.helloworld.controller.customers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.vendhq.api.ApiClient;
import com.vendhq.api.ApiException;
import com.vendhq.api.CustomersApi;
import com.vendhq.models.CustomerCollection;

/**
 * @author sundeepgarla
 *
 */
@RestController
public class VendCustomersController {
	@Autowired()
	private ApiClient apiClient;

	@RequestMapping(method = RequestMethod.GET, name = "/customers")
	public CustomerCollection getCustomers() throws ApiException {
		// Retrieve Customers
		CustomersApi apiInstance = new CustomersApi(apiClient);
		Integer pageSize = 56; // Integer | The maximum number of items to be returned in the response.
		try {
			CustomerCollection result = apiInstance.listCustomers(null, null, pageSize);
			System.out.println(result);
			return result;
		} catch (ApiException e) {
			System.err.println("Exception when calling CustomersApi#listCustomers");
			e.printStackTrace();
			throw e;
		}
	}
}
