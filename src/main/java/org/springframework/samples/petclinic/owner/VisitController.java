/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller that handles all HTTP-facing visit operations for a
 * specific pet belonging to a specific owner in the PetClinic application.
 *
 * <p>
 * Acts as part of the service/business-logic layer by translating web requests
 * into operations on the {@link OwnerRepository}, applying validation, and
 * selecting the appropriate Thymeleaf view. The single supported flow is
 * booking a new visit for an existing pet.
 *
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Dave Syer
 * @author Wick Dynex
 */
@Controller
class VisitController {

	private final OwnerRepository owners;

	public VisitController(OwnerRepository owners) {
		this.owners = owners;
	}

	/**
	 * Restricts which fields may be bound from incoming requests. The
	 * {@code id} fields are disallowed so that callers cannot overwrite the
	 * primary key of a visit (or any nested entity) through form submission.
	 * @param dataBinder the binder used for the current request
	 */
	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Resolves the {@link Visit} model attribute used by the new-visit form,
	 * and populates the request model with the owning {@link Owner} and
	 * {@link Pet}. Called before each request-mapped method so that data is
	 * always loaded fresh and the {@code Pet} reference retains its id even
	 * though id is not bound from form fields.
	 * @param ownerId the id of the owning {@link Owner}
	 * @param petId the id of the {@link Pet} the visit is being recorded for
	 * @param model the request model into which {@code owner} and {@code pet}
	 * are placed for the view
	 * @return a new, unsaved {@link Visit} associated with the resolved pet
	 * @throws IllegalArgumentException if no owner matches {@code ownerId} or
	 * the owner has no pet with {@code petId}
	 */
	@ModelAttribute("visit")
	public Visit loadPetWithVisit(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			Map<String, Object> model) {
		Optional<Owner> optionalOwner = owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));

		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}
		model.put("pet", pet);
		model.put("owner", owner);

		Visit visit = new Visit();
		pet.addVisit(visit);
		return visit;
	}

	/**
	 * Renders the empty form used to record a new visit for an existing pet.
	 * Spring MVC invokes {@link #loadPetWithVisit(int, int, Map)} first, so
	 * the {@code visit}, {@code pet}, and {@code owner} model attributes are
	 * already populated when this method runs.
	 * @return the logical view name of the new-visit form
	 */
	@GetMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	public String initNewVisitForm() {
		return "pets/createOrUpdateVisitForm";
	}

	/**
	 * Persists a new visit submitted from the new-visit form. Spring MVC
	 * invokes {@link #loadPetWithVisit(int, int, Map)} first so the owner and
	 * pet are already resolved. On validation failure the form is
	 * redisplayed; on success the owner's details page is shown with a
	 * confirmation flash message.
	 * @param owner the owning {@link Owner} resolved from the URL path
	 * @param petId the id of the {@link Pet} the visit belongs to
	 * @param visit the bound visit to validate and save
	 * @param result the binding result holding any validation errors
	 * @param redirectAttributes used to carry flash messages across redirects
	 * @return the logical view name or redirect target
	 */
	@PostMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	public String processNewVisitForm(@ModelAttribute Owner owner, @PathVariable int petId, @Valid Visit visit,
			BindingResult result, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			return "pets/createOrUpdateVisitForm";
		}

		owner.addVisit(petId, visit);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "Your visit has been booked");
		return "redirect:/owners/{ownerId}";
	}

}
