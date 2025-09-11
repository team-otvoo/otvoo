package com.team1.otvoo.clothes.dto;

import com.team1.otvoo.clothes.dto.clothesAttributeDef.ClothesAttributeWithDefDto;
import com.team1.otvoo.clothes.entity.ClothesType;
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
public class OotdDto{
  UUID clothesId;
  String name;
  String imageUrl;
  ClothesType type;
  List<ClothesAttributeWithDefDto> attributes;
}
