package com.team1.otvoo.clothes.repository;

import com.team1.otvoo.clothes.entity.Clothes;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothesRepository extends JpaRepository<Clothes, UUID>, ClothesRepositoryCustom {

  @Query("""

      select distinct c
    from Clothes c
    left join fetch c.selectedValues sv
    left join fetch sv.definition d
    left join fetch sv.value v
    where c.owner.id = :userId
    """)
  List<Clothes> findByUserIdFetch(@Param("userId") UUID userId);

  @Query("""
    select distinct c
    from Clothes c
    left join fetch c.selectedValues sv
    left join fetch sv.definition d
    left join fetch sv.value v
    where c.id in :ids
    """)
  List<Clothes> findByIdIn(@Param("ids") List<UUID> ids);

  @Query("""
      
  select distinct c.id
  from Clothes c
  join c.selectedValues sv
  join sv.definition d
  where d.id = :definitionId
""")
  List<UUID> findClothesIdsByDefinitionId(@Param("definitionId") UUID definitionId);

  @Query("""
      
  select distinct c
  from Clothes c
  join fetch c.selectedValues sv
  join fetch sv.definition d
  where c.id in :clothesIds
""")
  List<Clothes> findAllWithSelectedValuesByClothesIds(@Param("clothesIds") List<UUID> clothesIds);


  @Query("""
    select distinct c
    from Clothes c
    left join fetch c.selectedValues sv
    left join fetch sv.definition d
    left join fetch sv.value v
    where (c.owner.id = :userId
    and not exists (
         select sv.id
         from c.selectedValues sv
         where sv.definition.name = '계절'
      ))
        or (c.owner.id = :userId and d.name = '계절' and v.value in :seasons)
""")
  List<Clothes> findByUserIdAndSeasons(@Param("userId") UUID userId,
      @Param("seasons") List<String> seasons);
  }
