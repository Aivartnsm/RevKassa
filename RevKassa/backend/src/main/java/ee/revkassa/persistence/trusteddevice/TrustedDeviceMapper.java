package ee.revkassa.persistence.trusteddevice;

import ee.revkassa.controller.admin.device.dto.DeviceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TrustedDeviceMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "createdBy.displayName", target = "createdByName")
    @Mapping(source = "lastUsedAt", target = "lastUsedAt")
    @Mapping(source = "revokedAt", target = "revokedAt")
    @Mapping(source = "revokedBy.displayName", target = "revokedByName")
    DeviceDto toDeviceDto(TrustedDevice device);

    List<DeviceDto> toDeviceDtos(List<TrustedDevice> devices);
}
