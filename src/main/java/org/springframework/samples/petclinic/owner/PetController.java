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

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller that handles all HTTP-facing pet operations for a
 * specific owner in the PetClinic application.
 *
 * <p>
 * Scoped to the {@code /owners/{ownerId}} path prefix, this controller acts as
 * part of the service/business-logic layer by translating web requests into
 * operations on the {@link OwnerRepository} and {@link PetTypeRepository},
 * applying validation, and selecting the appropriate Thymeleaf view. Supported
 * flows include creating a new pet for an owner and editing an existing pet's
 * details.
 *
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Wick Dynex
 */
@Controller
@RequestMapping("/owners/{ownerId}")
class PetController {

	private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";

	private final OwnerRepository owners;

	private final PetTypeRepository types;

	public PetController(OwnerRepository owners, PetTypeRepository types) {
		this.owners = owners;
		this.types = types;
	}

	/**
	 * Populates the {@code types} model attribute with all available
	 * {@link PetType} values, used to render the pet type dropdown in the
	 * create and edit forms.
	 * @return the collection of all known pet types
	 */
	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		return this.types.findPetTypes();
	}

	/**
	 * Resolves the {@link Owner} model attribute identified by the URL path
	 * variable. Every request handled by this controller is scoped to a
	 * specific owner, so the owner must exist.
	 * @param ownerId the id of the owner taken from the URL path
	 * @return the resolved owner instance
	 * @throws IllegalArgumentException if no owner matches the given id
	 */
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable("ownerId") int ownerId) {
		return this.owners.findById(ownerId).orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct."));
	}

	/**
	 * Resolves the {@link Pet} model attribute for the current request.
	 * Returns a new, empty {@code Pet} when no pet id is present in the path
	 * (creation flow), or loads the pet belonging to the owner by id (edit
	 * flow).
	 * @param ownerId the id of the owning {@link Owner}
	 * @param petId the id of the pet to load, or {@code null} for creation
	 * @return the resolved pet instance
	 * @throws IllegalArgumentException if the owner cannot be found
	 */
	@ModelAttribute("pet")
	public Pet findPet(@PathVariable("ownerId") int ownerId,
			@PathVariable(name = "petId", required = false) Integer petId) {

		if (petId == null) {
			return new Pet();
		}

		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		return owner.getPet(petId);
	}

	/**
	 * Restricts which {@link Owner} fields may be bound from incoming
	 * requests. The {@code id} fields are disallowed so that callers cannot
	 * overwrite the owner's primary key (or that of any nested entity)
	 * through form submission.
	 * @param dataBinder the binder used for the current request
	 */
	@InitBinder("owner")
	public void initOwnerBinder(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Configures the {@link Pet} data binder for the current request,
	 * registering the {@link PetValidator} and disallowing binding of any
	 * {@code id} fields to prevent primary-key tampering via form submission.
	 * @param dataBinder the binder used for the current request
	 */
	@InitBinder("pet")
	public void initPetBinder(WebDataBinder dataBinder) {
		dataBinder.setValidator(new PetValidator());
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Renders the empty form used to create a new pet for the given owner.
	 * A new {@link Pet} is associated with the owner so that the form has a
	 * backing instance.
	 * @param owner the owner the new pet will belong to
	 * @param model the UI model (unused here but provided by Spring MVC)
	 * @return the logical view name of the create/update pet form
	 */
	@GetMapping("/pets/new")
	public String initCreationForm(Owner owner, ModelMap model) {
		Pet pet = new Pet();
		owner.addPet(pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Persists a new pet submitted from the creation form. Rejects pets whose
	 * name duplicates an existing pet on the same owner, and rejects birth
	 * dates set in the future. On validation failure the form is redisplayed;
	 * on success the user is redirected to the owner's details page.
	 * @param owner the owning {@link Owner} resolved from the URL path
	 * @param pet the bound pet to validate and save
	 * @param result the binding result holding any validation errors
	 * @param redirectAttributes used to carry flash messages across redirects
	 * @return the logical view name or redirect target
	 */
	@PostMapping("/pets/new")
	public String processCreationForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		if (StringUtils.hasText(pet.getName()) && pet.isNew() && owner.getPet(pet.getName(), true) != null) {
			result.rejectValue("name", "duplicate", "already exists");
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		owner.addPet(pet);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Pet has been Added");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Renders the prefilled form used to edit an existing pet. The pet and
	 * its owner are loaded into the model by {@link #findPet(int, Integer)}
	 * and {@link #findOwner(int)}.
	 * @return the logical view name of the create/update pet form
	 */
	@GetMapping("/pets/{petId}/edit")
	public String initUpdateForm() {
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Persists edits to an existing pet. Rejects a name change that collides
	 * with another pet belonging to the same owner, and rejects birth dates
	 * set in the future. On validation failure the form is redisplayed; on
	 * success the user is redirected to the owner's details page.
	 * @param owner the owning {@link Owner} resolved from the URL path
	 * @param pet the bound pet carrying the submitted edits
	 * @param result the binding result holding any validation errors
	 * @param redirectAttributes used to carry flash messages across redirects
	 * @return the logical view name or redirect target
	 */
	@PostMapping("/pets/{petId}/edit")
	public String processUpdateForm(Owner owner, @Valid Pet pet, BindingResult result,
			RedirectAttributes redirectAttributes) {

		String petName = pet.getName();

		// checking if the pet name already exists for the owner
		if (StringUtils.hasText(petName)) {
			Pet existingPet = owner.getPet(petName, false);
			if (existingPet != null && !Objects.equals(existingPet.getId(), pet.getId())) {
				result.rejectValue("name", "duplicate", "already exists");
			}
		}

		LocalDate currentDate = LocalDate.now();
		if (pet.getBirthDate() != null && pet.getBirthDate().isAfter(currentDate)) {
			result.rejectValue("birthDate", "typeMismatch.birthDate");
		}

		if (result.hasErrors()) {
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		updatePetDetails(owner, pet);
		redirectAttributes.addFlashAttribute("message", "Pet details has been edited");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Updates the pet details if it exists or adds a new pet to the owner.
	 * @param owner The owner of the pet
	 * @param pet The pet with updated details
	 */
	private void updatePetDetails(Owner owner, Pet pet) {
		Integer id = pet.getId();
		Assert.state(id != null, "'pet.getId()' must not be null");
		Pet existingPet = owner.getPet(id);
		if (existingPet != null) {
			// Update existing pet's properties
			existingPet.setName(pet.getName());
			existingPet.setBirthDate(pet.getBirthDate());
			existingPet.setType(pet.getType());
		}
		else {
			owner.addPet(pet);
		}
		this.owners.save(owner);
	}

}
