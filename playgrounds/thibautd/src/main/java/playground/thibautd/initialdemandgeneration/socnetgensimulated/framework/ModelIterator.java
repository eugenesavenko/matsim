/* *********************************************************************** *
 * project: org.matsim.*
 * ModelIterator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.thibautd.initialdemandgeneration.socnetgensimulated.framework;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;

import org.apache.log4j.Logger;

import playground.thibautd.initialdemandgeneration.socnetgen.framework.SnaUtils;
import playground.thibautd.socnetsim.population.SocialNetwork;

/**
 * @author thibautd
 */
public class ModelIterator {
	private static final Logger log =
		Logger.getLogger(ModelIterator.class);


	private final double targetClustering;
	private final double targetDegree;

	private final double precisionClustering;
	private final double precisionDegree;

	private final double initialPrimaryStep;
	private final double initialSecondaryStep;

	// TODO: make adaptive (the closer to the target value,
	// the more precise is should get)
	private double samplingRateClustering = 1;
	private final List<EvolutionListener> listeners = new ArrayList< >();

	public ModelIterator( final SocialNetworkGenerationConfigGroup config ) {
		this.targetClustering = config.getTargetClustering();
		this.targetDegree = config.getTargetDegree();

		listeners.add( new EvolutionLogger() );

		setSamplingRateClustering( config.getSamplingRateForClusteringEstimation() );
		this.precisionClustering = config.getPrecisionClustering();
		this.precisionDegree = config.getPrecisionDegree();

		this.initialPrimaryStep = config.getInitialPrimaryStep();
		this.initialSecondaryStep = config.getInitialSecondaryStep();
	}

	public SocialNetwork iterateModelToTarget(
			final ModelRunner runner,
			final Collection<Thresholds> initialThresholds ) {
		final ThresholdMemory memory = new ThresholdMemory( initialThresholds );

		for ( int iter=1; true; iter++ ) {
			log.info( "Iteration # "+iter );
			final Thresholds thresholds =
					memory.createNewThresholds( );

			log.info( "generate network for "+thresholds );
			final SocialNetwork sn = runner.runModel( thresholds );

			thresholds.setResultingAverageDegree( SnaUtils.calcAveragePersonalNetworkSize( sn ) );
			thresholds.setResultingClustering( SnaUtils.estimateClusteringCoefficient( samplingRateClustering , sn ) );

			final boolean added = memory.add( thresholds );

			for ( EvolutionListener l : listeners ) l.handleNewResult( thresholds , added );


			if ( isAcceptable( thresholds ) ) {
				log.info( thresholds+" fulfills the precision criteria!" );
				return sn;
			}
		}
	}

	public void addListener( final EvolutionListener l ) {
		listeners.add( l );
	}

	public void setSamplingRateClustering( final double rate ) {
		if ( rate < 0 || rate > 1 ) throw new IllegalArgumentException( rate+" is not in [0;1]" );
		this.samplingRateClustering = rate;
	}

	private boolean isAcceptable(
			final Thresholds thresholds ) {
		return distClustering( thresholds ) < precisionClustering &&
			distDegree( thresholds ) < precisionDegree;
	}

	private double distClustering( final Thresholds thresholds ) {
		return Math.abs( targetClustering -  thresholds.getResultingClustering() );
	}

	private double distDegree( final Thresholds thresholds ) {
		return Math.abs( targetDegree -  thresholds.getResultingAverageDegree() );
	}

	private class ThresholdMemory {
		private final Queue<Thresholds> queue = new ArrayDeque< >();

		private Thresholds bestNetSize = null;
		private Thresholds bestClustering = null;

		private double primaryStepSizeDegree = initialPrimaryStep;
		private double secondaryStepSizeDegree = initialSecondaryStep;

		private double primaryStepSizeClustering = initialPrimaryStep;
		private double secondaryStepSizeClustering = initialSecondaryStep;

		boolean hadDegreeImprovement = false;
		boolean hadClusteringImprovement = false;

		public ThresholdMemory( final Collection<Thresholds> initial ) {
			this.queue.addAll( initial );
		}

		public boolean add( final Thresholds t ) {
			boolean added = false;
			if ( bestNetSize == null || distDegree( t ) < distDegree( bestNetSize ) ) {
				log.info( t+" better than best value for net size "+bestNetSize );
				log.info( "replacing value" );
				bestNetSize = t;
				hadDegreeImprovement = true;
				added = true;
			}
			else {
				log.info( t+" not better than best value for net size "+bestNetSize+" => NOT KEPT" );
			}
			
			if (  bestClustering == null || distClustering( t ) < distClustering( bestClustering ) ) {
				log.info( t+" better than best value for clustering "+bestClustering );
				log.info( "replacing value" );
				bestClustering = t;
				hadClusteringImprovement = true;
				added = true;
			}
			else {
				log.info( t+" not better than best value for clustering "+bestClustering+" => NOT KEPT" );
			}

			return added;
		}

		public Thresholds createNewThresholds() {
			if ( queue.isEmpty() ) fillQueue();
			return queue.remove();
		}

		private void fillQueue() {
			// no improvement means "overshooting": decrease step sizes
			if ( !hadDegreeImprovement ) {
				primaryStepSizeDegree /= 2;
				secondaryStepSizeDegree /= 2;
			}

			if ( !hadClusteringImprovement ) {
				primaryStepSizeClustering /= 2;
				secondaryStepSizeClustering /= 2;
			}

			hadDegreeImprovement = false;
			hadClusteringImprovement = false;

			log.info( "New step Sizes:" );
			log.info( "primary - degree: "+primaryStepSizeDegree );
			log.info( "secondary - degree: "+secondaryStepSizeDegree );
			log.info( "primary - Clustering: "+primaryStepSizeClustering );
			log.info( "secondary - Clustering: "+secondaryStepSizeClustering );


			fillQueueWithChildren(
					bestNetSize,
					bestNetSize.getResultingAverageDegree() > targetDegree ? primaryStepSizeDegree : -primaryStepSizeDegree,
					bestNetSize.getResultingClustering() > targetClustering ? -secondaryStepSizeDegree : secondaryStepSizeDegree );

			if ( bestNetSize != bestClustering ) {
				fillQueueWithChildren(
						bestClustering,
						bestClustering.getResultingAverageDegree() > targetDegree ? primaryStepSizeClustering : -primaryStepSizeClustering,
						bestClustering.getResultingClustering() > targetClustering ? -secondaryStepSizeClustering : secondaryStepSizeClustering );
				fillQueueWithCombinations();
			}
		}

		private void fillQueueWithCombinations() {
			// risk of re-exploring...
			// And when adding, cannot simple modify step size
			queue.add( new Thresholds( bestNetSize.getPrimaryThreshold() , bestClustering.getSecondaryReduction() ) );
			queue.add( new Thresholds( bestClustering.getPrimaryThreshold() , bestNetSize.getSecondaryReduction() ) );

			queue.add(
					new Thresholds(
						( bestNetSize.getPrimaryThreshold() + bestClustering.getPrimaryThreshold() ) / 2d ,
						( bestNetSize.getSecondaryReduction() + bestClustering.getSecondaryReduction() ) / 2d ) );
		}

		private void fillQueueWithChildren( final Thresholds point , final double stepDegree , final double stepSecondary ) {
			queue.add( moveByStep( point , stepDegree , stepSecondary ) );
			queue.add( moveByStep( point , 0 , stepSecondary ) );
			queue.add( moveByStep( point , stepDegree , 0 ) );
		}

		private Thresholds moveByStep(
				final Thresholds point,
				final double degreeStep,
				final double clusteringStep ) {
			return new Thresholds(
					point.getPrimaryThreshold() + degreeStep,
					point.getSecondaryReduction() + clusteringStep );
		}
	}

	public static interface EvolutionListener {
		public void handleNewResult( Thresholds t , boolean keptInMemory );
	}

	private static class EvolutionLogger implements EvolutionListener {
		@Override
		public void handleNewResult( final Thresholds t , final boolean keptInMemory ) {
			log.info( "generated network for "+t );
		}
	}
}

