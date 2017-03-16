package playground.gleich.av_bus.runScenario;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.av.intermodal.router.VariableAccessTransitRouterModule;
import org.matsim.contrib.av.intermodal.router.config.VariableAccessConfigGroup;
import org.matsim.contrib.av.intermodal.router.config.VariableAccessModeConfigGroup;
import org.matsim.contrib.dvrp.data.FleetImpl;
import org.matsim.contrib.dvrp.data.file.VehicleReader;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.contrib.taxi.run.TaxiConfigConsistencyChecker;
import org.matsim.contrib.taxi.run.TaxiConfigGroup;
import org.matsim.contrib.taxi.run.TaxiOptimizerModules;
import org.matsim.contrib.taxi.run.TaxiOutputModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.TransitScheduleReaderV1;
import org.matsim.vehicles.VehicleReaderV1;

import playground.gleich.av_bus.FilePaths;

public class RunTaxi {
// Override FixedDistanceBasedVariableAccessModule in order to return taxi only for access/egress trips originating or ending within study area
	public static void main(String[] args) {
		Config config = ConfigUtils.loadConfig(FilePaths.PATH_CONFIG_BERLIN__10PCT_NULLFALL,
				new TaxiConfigGroup(), new DvrpConfigGroup());
		VariableAccessConfigGroup vacfg = new VariableAccessConfigGroup();
		vacfg.setVariableAccessAreaShpFile(FilePaths.PATH_STUDY_AREA_SHP);
		vacfg.setVariableAccessAreaShpKey(FilePaths.STUDY_AREA_SHP_KEY);
		vacfg.setStyle("fixed"); //FixedDistanceBasedVariableAccessModule
		{
			VariableAccessModeConfigGroup taxi = new VariableAccessModeConfigGroup();
			taxi.setDistance(20000);
			taxi.setTeleported(false);
			taxi.setMode("taxi");
			vacfg.setAccessModeGroup(taxi);
		}
		{
			VariableAccessModeConfigGroup walk = new VariableAccessModeConfigGroup();
			walk.setDistance(1000);
			walk.setTeleported(true);
			walk.setMode("walk");
			vacfg.setAccessModeGroup(walk);
		}
		config.addModule(vacfg);

		config.transitRouter().setSearchRadius(15000);
		config.transitRouter().setExtensionRadius(0);
		config.addConfigConsistencyChecker(new TaxiConfigConsistencyChecker());
		config.checkConsistency();

		Scenario scenario = ScenarioUtils.createScenario(config);
		new MatsimNetworkReader(scenario.getNetwork()).readFile(FilePaths.PATH_NETWORK_BERLIN__10PCT);
		new TransitScheduleReaderV1(scenario).readFile(FilePaths.PATH_TRANSIT_SCHEDULE_BERLIN__10PCT_WITHOUT_BUSES_IN_STUDY_AREA);
		new VehicleReaderV1(scenario.getTransitVehicles()).readFile(FilePaths.PATH_TRANSIT_VEHICLES_BERLIN__10PCT);
		new PopulationReader(scenario).readFile(FilePaths.PATH_POPULATION_BERLIN__10PCT_FILTERED);
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(100);
		config.controler().setOutputDirectory(FilePaths.PATH_OUTPUT_BERLIN__10PCT_TAXI_100);
		config.controler().setWritePlansInterval(10);
		config.qsim().setEndTime(60*60*60); // [geloest durch maximum speed in transit_vehicles-datei: bei Stunde 50:00:00 immer noch 492 Veh unterwegs (nur pt veh., keine Agenten), alle pt-fahrten stark verspätet, da pünktlicher start, aber niedrigere Geschwindigkeit als im Fahrplan geplant]
		config.controler().setWriteEventsInterval(10);	
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		
		FleetImpl fleet = new FleetImpl();
		String taxiFileName = TaxiConfigGroup.get(config).getTaxisFileUrl(config.getContext()).getFile();
		new VehicleReader(scenario.getNetwork(), fleet).readFile(taxiFileName);
		
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new TaxiOutputModule());
        controler.addOverridingModule(TaxiOptimizerModules.createDefaultModule(fleet));
		controler.addOverridingModule(new VariableAccessTransitRouterModule());
		
		controler.run();
	}

}
