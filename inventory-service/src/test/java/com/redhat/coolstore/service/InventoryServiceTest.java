package com.redhat.coolstore.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import javax.persistence.EntityManager;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.redhat.coolstore.model.Inventory;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class InventoryServiceTest {
	@Mock private EntityManager em;

	@InjectMocks private InventoryService inventoryService;

	@Before
	public void configureMocks() {
		when(em.find(eq(Inventory.class), anyString())).thenAnswer(new Answer<Inventory>() {
			@Override
			public Inventory answer(InvocationOnMock mock) throws Throwable {
				return new Inventory(mock.getArgument(1), 10, "London", "http://link");
			}
		});

	}

	@Test
	public void noInventoryForRecalledProducts() {
		List<String> recalledProducts = Arrays.asList("165613","165614");

		for (String recalledProduct : recalledProducts) {
			// method under test
			Inventory returnedInventory = inventoryService.getInventory(recalledProduct);

			// verify
			assertEquals("product " + recalledProduct + " is recalled inventory", 0, returnedInventory.getQuantity());
		}
	}
}
