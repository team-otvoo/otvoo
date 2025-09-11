package com.team1.otvoo.clothes.dto.clothesAttributeDef;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ClothesAttributeWithDefDto {
  UUID definitionId;
  String definitionName;
  List<String> selectableValues;
  String value;
}
