package org.github.nynosy.adiresy_mobile.ui.map;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.List;

import org.github.nynosy.adiresy_mobile.data.AdiresyRepository;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.cache.AdminUnitEntity;

public class MapViewModel extends AndroidViewModel {

    private static class AdminQuery {
        final String type;
        final String parentUuid;
        AdminQuery(String type, String parentUuid) {
            this.type = type;
            this.parentUuid = parentUuid;
        }
    }

    private final AdiresyRepository repository;
    private final MutableLiveData<AdminQuery>      adminQuery       = new MutableLiveData<>();
    private final MutableLiveData<AdminUnitEntity> selectedBoundary = new MutableLiveData<>();

    public MapViewModel(@NonNull Application application) {
        super(application);
        repository = AdiresyRepository.getInstance(application);
    }

    public LiveData<Result<List<AdminUnitEntity>>> getAdminUnits() {
        return Transformations.switchMap(adminQuery, q -> {
            if (q == null) return new MutableLiveData<>();
            switch (q.type) {
                case "region":    return repository.listRegions();
                case "district":  return repository.listDistricts(q.parentUuid);
                case "commune":   return repository.listCommunes(q.parentUuid);
                default:          return repository.listFokontany(q.parentUuid);
            }
        });
    }

    public LiveData<AdminUnitEntity> getSelectedBoundary() { return selectedBoundary; }

    public void loadRegions()                    { adminQuery.setValue(new AdminQuery("region",    null)); }
    public void loadDistricts(String parentUuid) { adminQuery.setValue(new AdminQuery("district",  parentUuid)); }
    public void loadCommunes(String parentUuid)  { adminQuery.setValue(new AdminQuery("commune",   parentUuid)); }
    public void loadFokontany(String parentUuid) { adminQuery.setValue(new AdminQuery("fokontany", parentUuid)); }

    /** Called when the user selects a fokontany — map should draw its boundary. */
    public void showBoundary(AdminUnitEntity unit) {
        selectedBoundary.setValue(unit);
    }
}
