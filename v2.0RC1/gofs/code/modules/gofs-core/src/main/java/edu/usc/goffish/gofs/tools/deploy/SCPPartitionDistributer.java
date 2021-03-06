/*
*    Copyright 2013 University of Southern California
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License. 
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package edu.usc.goffish.gofs.tools.deploy;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

import org.apache.commons.io.*;

import edu.usc.goffish.gofs.partition.*;
import edu.usc.goffish.gofs.slice.*;
import edu.usc.goffish.gofs.util.*;

public class SCPPartitionDistributer implements IPartitionDistributer {

	private static final String DefaultSCPBinary = "scp";

	private final Path _scpBinaryPath;
	private final String[] _extraSCPOptions;
	private final ISliceSerializer _serializer;
	private final int _instancesGroupingSize;
	private final int _numSubgraphBins;

	public SCPPartitionDistributer() {
		this(Paths.get(DefaultSCPBinary), null, 1, -1);
	}

	public SCPPartitionDistributer(ISliceSerializer serializer, int instancesGroupingSize, int numSubgraphBins) {
		this(Paths.get(DefaultSCPBinary), null, serializer, instancesGroupingSize, numSubgraphBins);
	}

	public SCPPartitionDistributer(Path scpBinaryPath, String[] extraSCPOptions, int instancesGroupingSize, int numSubgraphBins) {
		this(scpBinaryPath, extraSCPOptions, new JavaSliceSerializer(), instancesGroupingSize, numSubgraphBins);
	}

	public SCPPartitionDistributer(Path scpBinaryPath, String[] extraSCPOptions, ISliceSerializer serializer, int instancesGroupingSize, int numSubgraphBins) {
		if (scpBinaryPath == null) {
			throw new IllegalArgumentException();
		}
		if (serializer == null) {
			throw new IllegalArgumentException();
		}
		if (instancesGroupingSize < 1) {
			throw new IllegalArgumentException();
		}
		if (numSubgraphBins < 1 && numSubgraphBins != -1) {
			throw new IllegalArgumentException();
		}

		_scpBinaryPath = scpBinaryPath.normalize();
		_extraSCPOptions = extraSCPOptions;
		_serializer = serializer;
		_instancesGroupingSize = instancesGroupingSize;
		_numSubgraphBins = numSubgraphBins;
	}

	@Override
	public UUID distribute(URI location, ISerializablePartition partition) throws IOException {
		Path workingDir = Files.createTempDirectory("gofs_scpdist");

		System.out.print("writing partition... ");
		long total = 0;

		int numSubgraphBins = _numSubgraphBins;
		if (_numSubgraphBins == -1 || _numSubgraphBins > partition.size()) {
			numSubgraphBins = partition.size();
		}

		// write slices for partition
		ISliceManager sliceManager = SliceManager.create(_serializer, new FileStorageManager(workingDir));
		total += sliceManager.writePartition(partition, _instancesGroupingSize, numSubgraphBins);

		System.out.println("[" + total / 1000 + " KB]");

		// prepare list of files to scp
		List<Path> sliceFiles = new LinkedList<>();
		try (DirectoryStream<Path> sliceDir = Files.newDirectoryStream(workingDir)) {
			for (Path slice : sliceDir) {
				sliceFiles.add(slice);
			}
		}

		// scp files
		System.out.println("moving partition " + partition.getId() + " to " + location + "...");

		SCPHelper.SCP(_scpBinaryPath, _extraSCPOptions, sliceFiles, location);

		// delete files
		FileUtils.deleteQuietly(workingDir.toFile());

		// append partition UUID information as fragment to location and return location
		return sliceManager.getPartitionUUID();
	}
}
