package com.epitomejob.profile;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/profiles")
public class ProfileController {
  private final ProfileService service;
  public ProfileController(ProfileService service) { this.service = service; }
  @GetMapping("/me")
  public Profile getMe() { return service.getMe(); }
  @PutMapping("/me")
  public Profile upsertMe(@Valid @RequestBody Profile incoming) { return service.upsertMe(incoming); }
}
