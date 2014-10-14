/*
 * Copyright (C) 2014 Open Access Button
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */

package org.openaccessbutton.openaccessbutton.map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.ui.IconGenerator;

import org.openaccessbutton.openaccessbutton.MainActivity;
import org.openaccessbutton.openaccessbutton.OnShareIntentInterface;
import org.openaccessbutton.openaccessbutton.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shows paywalled journal requests, just like the map on openaccessbutton.org.
 */
public class MapFragment extends Fragment {
    private MapView m;
    private ClusterManager<Item> mClusterManager;
    private Item mClickedClusterItem;
    private Map<String, Item> mMarkers;  // For unique markers
    private final String WEB_MAP_URL = "http://openaccessbutton.org";
    private OnShareIntentInterface mCallback;

    public MapFragment() {
        // Required empty public constructor
    }

    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (OnShareIntentInterface) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnShareIntentInterface");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_map, container, false);
        m = (MapView) v.findViewById(R.id.map_view);
        m.onCreate(savedInstanceState);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        setupMap();
    }

    class MarkerInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {
        @Override
        public View getInfoContents(Marker marker) {
            LayoutInflater li = getActivity().getLayoutInflater();
            View view = li.inflate(R.layout.map_info_window, null);

            TextView name = (TextView) view.findViewById(R.id.map_item_name);
            TextView story = (TextView) view.findViewById(R.id.map_item_story);
            TextView description = (TextView) view.findViewById(R.id.map_item_description);
            name.setText(mClickedClusterItem.name());
            story.setText(mClickedClusterItem.mStory);
            description.setText(mClickedClusterItem.mDescription);

            return view;
        }

        @Override
        public View getInfoWindow(Marker arg0) {
            return null;
        }
    }

    public void setupMap() {
        MapsInitializer.initialize(getActivity());
        mMarkers = new HashMap<String, Item>();

        // Auto center map
        Runnable r = new Runnable() {
            @Override
            public void run() {
                // Based on http://stackoverflow.com/questions/17668917
                LocationManager lm = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
                List<String> providers = lm.getProviders(true);
                Location location = null;

                // Try every possible provider
                for (int i=providers.size()-1; i>=0; i--) {
                    location = lm.getLastKnownLocation(providers.get(i));
                    if (location != null) break;
                }

                final LatLng center;
                if (location == null) {
                    center = new LatLng(51.503186, -0.126446);
                } else {
                    center = new LatLng(location.getLatitude(), location.getLongitude());
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        m.getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(center, 10));
                    }
                });
            }
        };
        (new Thread(r)).start();

        // Setup clustering
        mClusterManager = new ClusterManager<Item>(getActivity(), m.getMap());
        mClusterManager.setRenderer(new ItemRenderer());
        m.getMap().setOnCameraChangeListener(mClusterManager);
        m.getMap().setOnMarkerClickListener(mClusterManager);

        // Setup info windows
        m.getMap().setInfoWindowAdapter(mClusterManager.getMarkerManager());
        mClusterManager.getMarkerCollection().setOnInfoWindowAdapter(new MarkerInfoWindowAdapter());
        m.getMap().setOnMarkerClickListener(mClusterManager);
        mClusterManager.setOnClusterClickListener(new ClusterManager.OnClusterClickListener<Item>() {
            @Override
            public boolean onClusterClick(Cluster<Item> cluster) {
                float newZoomLevel = m.getMap().getCameraPosition().zoom + 1;
                if (newZoomLevel < m.getMap().getMinZoomLevel()) {
                    newZoomLevel = m.getMap().getMinZoomLevel();
                } else if (newZoomLevel > m.getMap().getMaxZoomLevel()) {
                    newZoomLevel = m.getMap().getMaxZoomLevel();
                }
                // TODO: Why does this not zoom when we use animateCamera()?
                m.getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(cluster.getPosition(), newZoomLevel));
                return false;
            }
        });
        mClusterManager.setOnClusterItemClickListener(new ClusterManager.OnClusterItemClickListener<Item>() {
            @Override
            public boolean onClusterItemClick(Item item) {
                mClickedClusterItem = item;
                return false;
            }
        });

        addSampleData();
    }

    @Override
    public void onResume() {
        super.onResume();
        m.onResume();
        updateShareIntent();
    }

    @Override
    public void onPause() {
        super.onPause();
        m.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        m.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        m.onLowMemory();
    }

    // Implement a custom Cluster renderer so we can use our own icons for the marker pins
    // Based upon android-maps-utils CustomMarkerClusteringDemoActivity
    public class ItemRenderer extends DefaultClusterRenderer<Item> {
        private final IconGenerator mIconGenerator = new IconGenerator(getActivity().getApplicationContext());
        private final ImageView mImageView;
        private final int mDimension;

        public ItemRenderer() {
            super(getActivity().getApplicationContext(), m.getMap(), mClusterManager);

            mImageView = new ImageView(getActivity().getApplicationContext());
            mDimension = (int) getResources().getDimension(R.dimen.map_marker_width);
            mImageView.setLayoutParams(new ViewGroup.LayoutParams(mDimension, mDimension));
            int padding = (int) getResources().getDimension(R.dimen.map_marker_padding);
            mImageView.setPadding(padding, padding, padding, padding);
            mIconGenerator.setContentView(mImageView);
        }

        @Override
        protected void onBeforeClusterItemRendered(Item item, MarkerOptions markerOptions) {
            // Draw a single person.
            // Set the info window to show their name.
            mImageView.setImageResource(item.mIcon);
            Bitmap icon = mIconGenerator.makeIcon();
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(icon)).title(item.toString());
        }
    }


    private void addItem(Item item) {
        // Don't add unique markers
        String key = item.mPosition.latitude + "," + item.mPosition.longitude;

        // Move the marker by up to 10m if it's got exactly the same lat/lng as another one
        if (mMarkers.containsKey(key)) {
            // DEGREE APPROXIMATIONS
            // 1 latitude degree approx = 111,111m
            // 1 longitude degree approx = 111,111*cos(latitude)
            double latitudeMaxOffset = 10.0/111111.0;
            double longitudeMaxOffset = 10.0/(111111.0*Math.cos(Math.toRadians((item.mPosition.latitude))));

            Random generator = new Random();
            double latitudeOffset = latitudeMaxOffset * generator.nextDouble();
            double longitudeOffset = longitudeMaxOffset * generator.nextDouble();

            LatLng newPosition = new LatLng(item.mPosition.latitude + latitudeOffset, item.mPosition.longitude + longitudeOffset);
            item.mPosition = newPosition;
            addItem(item);
        } else {
            mMarkers.put(key, item);
            mClusterManager.addItem(item);
        }

    }

    private void addSampleData() {
        // TODO: Find out where we can dynamically get this data from OAB and do it that way!!!!!
        addItem(new Item(52.5, 13.4, "Re-checking some work for my dissertation", "10.1016/j.neurobiolaging.2012.12.011", "Student", "http://www.sciencedirect.com/science/article/pii/S0197458012006422", "Nov 18, 2013", "Joseph McArthur", "Title: Fractalkine overexpression suppresses tau pathology in a mouse model of tauopathyAuthors: Kevin R. Nash, Daniel C. Lee, Jerry B. Hunt, Josh M. Morganti, Maj-Linda Selenica, Peter Moran, Patrick Reid, Milene Brownlow, Clement Guang-Yu Yang, Miloni Savalia, Carmelina Gemma, Paula C. Bickford, Marcia N. Gordon, David MorganJournal: Neurobiology of AgingDate: 2013-6"));
        addItem(new Item(52.5, 13.4, "Because I need it. ", "10.1007/s12253-011-9470-z", "Student", "http://www.ncbi.nlm.nih.gov/pubmed/22102006", "Nov 18, 2013", "David Carroll ", "Title: Prognostic Value of Raf Kinase Inhibitor Protein in Esophageal Squamous Cell CarcinomaAuthors: Chengcheng Gao, Liqun Pang, Chengcheng Ren, Tianheng MaJournal: Pathology & Oncology ResearchDate: 2012-4-19"));
        addItem(new Item(-42.9, 147.3, "I research kidneys, I need this paper to learn more!", "10.1038/nrneph.2013.247", "Student", "http://www.nature.com/nrneph/journal/vaop/ncurrent/full/nrneph.2013.247.html", "Nov 18, 2013", "Georgina Taylor", "Title: Basic research: Podocyte progenitors and ectopic podocytesAuthors: Laura Lasagni, Paola RomagnaniJournal: Nature Reviews NephrologyDate: 2013-11-12"));
        addItem(new Item(52.5200066, 13.404954, "Working on a paper about JATS, for which this paper seems relevant.", "10.1016/j.serrev.2012.08.006", "Student", "http://www.sciencedirect.com/science/article/pii/S0098791312000858", "Nov 18, 2013", "Daniel Mietchen", "Title: NISO Z39.96-201x, JATS: Journal Article Tag SuiteAuthors: Mark H. NeedlemanJournal: Serials ReviewDate: 2012-9"));
        addItem(new Item(52.5200066, 13.404954, "Working on a paper about semantic search, where this may be relevant.", "10.1016/j.future.2006.03.002", "Student", "http://www.sciencedirect.com/science/article/pii/S0167739X06000318", "Nov 18, 2013", "Daniel Mietchen", "Title: From bioinformatic web portals to semantically integrated Data Grid networksAuthors: Adriana Budura, Philippe CudrĂŠ-Mauroux, Karl AbererJournal: Future Generation Computer SystemsDate: 2007-3"));
        addItem(new Item(46.4982953, 11.3547582, "", "10.1016/0959-8022(94)90005-1", "Student", "http://www.sciencedirect.com/science/article/pii/0959802294900051", "Nov 18, 2013", "Daniel Graziotin", "Title: Formative contexts and information technology: Understanding the dynamics of innovation in organizationsAuthors: Claudio U. Ciborra, Giovan Francesco LanzaraJournal: Accounting, Management and Information TechnologiesDate: 1994-4"));
        addItem(new Item(55.9, -4.3, "It's about open access but I can't access it !!!", "10.1177/0340035209359573", "Student", "http://ifl.sagepub.com/content/36/1/40.full.pdf+html", "Nov 18, 2013", "Graham Steel", "Interactive open access publishing and public peer review: The effectiveness of transparency and self-regulation in scientific quality assurance "));
        addItem(new Item(46.4982953, 11.3547582, "", "10.1080/01972243.1992.9960124", "Student", "http://www.tandfonline.com/doi/abs/10.1080/01972243.1992.9960124#.Uon3SsQpNv4", "Nov 18, 2013", "Daniel Graziotin", "Title: From thinking to tinkering: The grassroots of strategic information systemsAuthors: Claudio U. CiborraJournal: The Information SocietyDate: 1992-12"));
        addItem(new Item(52.5, 13.4, "", "", "Student", "http://www.ncbi.nlm.nih.gov/pubmed/22102006", "Nov 18, 2013", "Marie Hauerslev", ""));
        addItem(new Item(51.5112139, -0.1198244, "I'd like to build a distributed system for archiving Open Access journal articles.", "10.1007/s00799-012-0080-5", "Other", "http://link.springer.com/article/10.1007%2Fs00799-012-0080-5", "Nov 18, 2013", "Alf Eaton", "Title: Extending OAI-PMH over structured P2P networks for digital preservationAuthors: Everton F. R. Seรกra, Marcos S. Sunye, Luis C. E. Bona, Tiago Vignatti, Andre L. Vignatti, Anne DoucetJournal: International Journal on Digital LibrariesDate: 2012-7-28"));
        addItem(new Item(37.09024, -95.712891, "", "10.1016/j.nut.2013.07.011", "Student", "http://www.sciencedirect.com/science/article/pii/S0899900713003468?np=y", "Nov 18, 2013", "Colby", "Title: Association between chocolate consumption and fatness in European adolescentsAuthors: Magdalena Cuenca-GarcĂ­a, Jonatan R. Ruiz, Francisco B. Ortega, Manuel J. CastilloJournal: NutritionDate: 2013-10"));
        addItem(new Item(53.9623008, -1.0818844, "This is a fascinating new area of science and I want to read further.", "10.1159/000355916", "Student", "http://www.karger.com/Article/FullText/355916", "Nov 18, 2013", "Jake Watson", "Title: Homeopathy: Meta-Analyses of Pooled Clinical DataAuthors: Robert G. HahnJournal: Forschende KomplementĂ¤rmedizin / Research in Complementary MedicineDate: 2013"));
        addItem(new Item(40.4167754, -3.7037902, "Research, find the state of the art.", "10.1002/dac.987", "Student", "http://onlinelibrary.wiley.com/doi/10.1002/dac.987/abstract", "Nov 18, 2013", "Javier Zazo", "Title: An uplink resource allocation scheme for OFDMA-based cognitive radio networksAuthors: Wei Wang, Wenbo Wang, Qianxi Lu, Tao PengJournal: International Journal of Communication SystemsDate: 2009-5"));
        addItem(new Item(55.378051, -3.435973, "Colleague asked for it - not available even with institutional affiliation", "10.1179/1756750513Z.00000000032", "Researcher", "http://www.ingentaconnect.com/content/maney/hen/2013/00000004/00000002/art00004", "Nov 18, 2013", "Victoria Pia Spry-Marques", "Title: Authority and Community: Reflections on Archaeological Practice at Heslington East, YorkAuthors: Cath Neal, Steve RoskamsJournal: The Historic EnvironmentDate: 2013-10-1"));
        addItem(new Item(50.9, 6.9, "Being a hashimoto's thyroiditis patient and writing a book about the mechanisms behind autoimmune dieseases, I need up-to-date scientific information like this article.", "10.1210/jc.2013-2667", "Patient", "http://jcem.endojournals.org/content/early/2013/10/31/jc.2013-2667.abstract", "Nov 18, 2013", "Andrea Kamphuis", "Title: Skewed X chromosome inactivation and female preponderance in autoimmune thyroid disease: an association study and meta-analysisAuthors: M. J. Simmonds, F. K. Kavvoura, O. J. Brand, P. R. Newby, L. E. Jackson, C. E. Hargreaves, J. A. Franklyn, S. C. L. GoughJournal: Journal of Clinical Endocrinology & MetabolismDate: 2013-11-1"));
        addItem(new Item(42.0, 21.4, "", "10.1007/978-3-642-37285-8_10", "Researcher", "http://link.springer.com/chapter/10.1007/978-3-642-37285-8_10", "Nov 20, 2013", "Dimitar Poposki", "Title: Designing Dippler â A Next-Generation TEL SystemAuthors: Mart Laanpere, Hans PĂľldoja, Peeter NormakDate: 2013"));
        addItem(new Item(42.0, 21.4, "", "10.1007/978-3-642-33299-9_10", "Researcher", "http://link.springer.com/chapter/10.1007/978-3-642-33299-9_10", "Nov 20, 2013", "Dimitar Poposki", "Title: A Dutch Repository for Open Educational Resources in Software Engineering: Does Downesâ Description Fit?Authors: Peter BeckerDate: 2012"));
        addItem(new Item(52.5200066, 13.404954, "Curious", "10.1007/s00068-008-8805-2", "Student", "http://link.springer.com/article/10.1007/s00068-008-8805-2?no-access=true", "Nov 18, 2013", "Stian Haklev", "Title: Overall Asessment of the Response to Terrorist Bombings in Trains, Madrid, 11 March 2004Authors: Fernando Turégano-Fuentes, Dolores Pérez-Díaz, Mercedes Sanz-Sánchez, Javier Ortiz AlonsoJournal: European Journal of Trauma and Emergency SurgeryDate: 2008-10-26"));
        addItem(new Item(52.5, 13.4, "", "10.1007/BF02614325", "Student", "http://link.springer.com/article/10.1007%2FBF02614325", "Nov 18, 2013", "Raniere Silva", "Title: Criss-cross methods: A fresh view on pivot algorithms"));
        addItem(new Item(52.5200066, 13.404954, "Want to demonstrate the benefit of using open educational resources, but can't share the great results of this article because I can't access it. ", "10.1080/02680513.2012.716657", "Advocate", "http://www.tandfonline.com/doi/abs/10.1080/02680513.2012.716657?journalCode=copl20#.UooWGmQ4V8s", "Nov 18, 2013", "Nicole Allen", "Title: One collegeâs use of an open psychology textbookAuthors: John Hilton, Carol LamanJournal: Open Learning: The Journal of Open, Distance and e-LearningDate: 2012-11"));
        addItem(new Item(55.378051, -3.435973, "", "1-4411-1296-0", "Student", "http://books.google.co.uk/books?id=hDtTGjE11-4C", "Nov 18, 2013", "Angelica Tavella", "British Army in Battle and Its Image 1914-18Stephen Badsey"));
        addItem(new Item(55.378051, -3.435973, "", "1-4411-1296-0", "Student", "http://books.google.co.uk/books?id=hDtTGjE11-4C", "Nov 18, 2013", "Angelica Tavella", "British Army in Battle and Its Image 1914-18Stephen Badsey"));
        addItem(new Item(52.5, 13.4, "", "1-4411-1296-0", "Student", "http://books.google.co.uk/books?id=hDtTGjE11-4C", "Nov 18, 2013", "Angelica Tavella", "British Army in Battle and Its Image 1914-18Stephen Badsey"));
        addItem(new Item(52.5, 13.4, "", "1-4411-1296-0", "Student", "http://books.google.co.uk/books?id=hDtTGjE11-4C", "Nov 18, 2013", "Angelica Tavella", "British Army in Battle and Its Image 1914-18Stephen Badsey"));
        addItem(new Item(52.5, 13.4, "I want to breed a genetically modified pig.", "10.1016/j.jgg.2012.07.014", "Student", "http://www.sciencedirect.com/science/article/pii/S1673852713000027", "Nov 18, 2013", "David", "Title: Genetically Modified Pig Models for Human DiseasesAuthors: Nana Fan, Liangxue LaiJournal: Journal of Genetics and GenomicsDate: 2013-2"));
        addItem(new Item(52.5200066, 13.404954, "Recherche zu Wikipedia Artikel https://de.wikipedia.org/wiki/Krebs_%28Medizin%29", "10.1002/ange.19901021106", "Student", "http://onlinelibrary.wiley.com/doi/10.1002/ange.19901021106/abstract;jsessionid=A50BA75812BA20A32C2289C1D1C45DD8.f02t01", "Nov 18, 2013", "Stefan Kasberger", "Title: Falsche Annahmen Ăźber die ZusammenhĂ¤nge zwischen der Umweltverschmutzung und der Entstehung von KrebsAuthors: Bruce N. Ames, Lois Swirsky GoldJournal: Angewandte ChemieDate: 1990-11"));
        addItem(new Item(52.5, 13.4, "", "1-4411-1296-0", "Student", "http://books.google.co.uk/books?id=hDtTGjE11-4C", "Nov 18, 2013", "Angelica Tavella", "British Army in Battle and Its Image 1914-18Stephen Badsey"));
        addItem(new Item(52.5, 13.4, "Test", "doi:10.1038/nchembio1106-568", "Advocate", "http://www.nature.com/nchembio/journal/v2/n11/full/nchembio1106-568.html", "Nov 18, 2013", "Gabriella Marino", "ElementsNature Chemical Biology 2, 568 (2006) doi:10.1038/nchembio1106-568ARTICLE TOOLSSend to a friendExport citationRights and permissionsOrder commercial reprintsSEARCH PUBMED FORMirella BucciRandy SchekmanMirella Bucci1"));
        addItem(new Item(52.5200066, 13.404954, "I *am* trying to save lives, dammit!", "10.1016/j.jpba.2008.06.024", "Researcher", "http://www.ncbi.nlm.nih.gov/pubmed/18703302", "Nov 18, 2013", "Mike Taylor", "Title: Detecting counterfeit antimalarial tablets by near-infrared spectroscopyAuthors: Floyd E. Dowell, Elizabeth B. Maghirang, Facundo M. Fernandez, Paul N. Newton, Michael D. GreenJournal: Journal of Pharmaceutical and Biomedical AnalysisDate: 2008-11"));
        addItem(new Item(43.7710332, 11.2480006, "Research paper", "10.1007/s11016-013-9845-8", "Student", "http://link.springer.com/article/10.1007%2Fs11016-013-9845-8", "Nov 18, 2013", "Chealsye Bowley", "Title: Michael Friedman and the âmarriageâ of history and philosophy of science (and history of philosophy)Authors: Thomas SturmJournal: MetascienceDate: 2013-10-23"));
        addItem(new Item(37.7749295, -122.4194155, "", "", "Student", "http://www.openaccessbutton.org/#top", "Nov 18, 2013", "Yan", ""));
        addItem(new Item(50.0755381, 14.4378005, "I need it for the researcher.", "10.1016/0010-0285(74)90015-2", "Student", "http://www.sciencedirect.com/science/article/pii/0010028574900152", "Nov 18, 2013", "Tereza Simandlová", "Title: Toward a theory of automatic information processing in readingAuthors: David LaBerge, S.Jay SamuelsJournal: Cognitive PsychologyDate: 1974-4"));
        addItem(new Item(37.7749295, -122.4194155, "", "10.1126/science.1240585", "Student", "http://www.sciencemag.org/content/342/6160/1240585.full.pdf", "Nov 18, 2013", "Yan", "Title: Molecular Architecture of a Eukaryotic Translational Initiation ComplexAuthors: I. S. Fernandez, X.-C. Bai, T. Hussain, A. C. Kelley, J. R. Lorsch, V. Ramakrishnan, S. H. W. ScheresJournal: ScienceDate: 2013-11-14"));
        addItem(new Item(50.0755381, 14.4378005, "Study reasons", "10.1016/B978-0-12-386492-5.00010-5", "Student", "http://www.sciencedirect.com/science/article/pii/B9780123864925000105", "Nov 18, 2013", "Tereza Simandlová", "Title: Media literacy and positive youth developmentAuthors: Michelle J. Boyd, Julie DobrowDate: 2011"));
        addItem(new Item(48.1, 11.6, "Archean Ziron Geochronology of Canada interests me.", "10.1086/673265", "Advocate", "http://www.jstor.org/discover/10.1086/673265?uid=371768741&uid=3737864&uid=2134&uid=2&uid=70&uid=3&uid=371764691&uid=67&uid=29932&uid=62&uid=5910216&sid=21102944507357", "Nov 18, 2013", "Frewin", "Title: Detrital Zircon Geochronology and Provenance of the Paleoproterozoic Huron (∼2.4–2.2 Ga) and Animikie (∼2.2–1.8 Ga) Basins, Southern Superior ProvinceAuthors: John P. Craddock, R. H. Rainbird, W. J. Davis, Cam Davidson, Jeffrey D. Vervoort, Alexandros Konstantinou, Terry Boerboom, Sarah Vorhies, Laura Kerber, Becky LundquistJournal: The Journal of GeologyDate: 2013-11"));
        addItem(new Item(6.4, 5.6, "Research materials", "", "Student", "https://www.openaccessbutton.org/#top", "Nov 20, 2013", "Mirabel Akure", "Journals"));
        addItem(new Item(33.1, -117.0, "", "", "Student", "https://www.openaccessbutton.org/#top", "Nov 20, 2013", "Sang Shin", ""));
        addItem(new Item(53.8, -1.5, "", "", "Academic", "https://www.openaccessbutton.org/#top", "Nov 25, 2013", "lewis", ""));
        addItem(new Item(48.3, 11.4, "I'm a freelance writer, I considered writing an article about this paper. Now I probably won't. ", "10.1038/emboj.2013.245", "Other", "http://www.nature.com/emboj/journal/vaop/ncurrent/full/emboj2013245a.html", "Nov 18, 2013", "Hans Zauner", "Title: Transcriptome sequencing during mouse brain development identifies long non-coding RNAs functionally involved in neurogenic commitmentAuthors: Julieta Aprea, Silvia Prenninger, Martina Dori, Tanay Ghosh, Laura Sebastian Monasor, Elke Wessendorf, Sara Zocher, Simone Massalini, Dimitra Alexopoulou, Mathias Lesche, Andreas Dahl, Matthias Groszer, Michael Hiller, Federico CalegariJournal: The EMBO JournalDate: 2013-11-15"));
        addItem(new Item(51.165691, 10.451526, "research for documentary", "10.1038/nrg3605", "Other", "http://www.nature.com/nrg/journal/v14/n12/pdf/nrg3605.pdf", "Nov 18, 2013", "Kerstin Haus", "Title: Evolution of crop species: genetics of domestication and diversificationAuthors: Rachel S. Meyer, Michael D. PuruggananJournal: Nature Reviews GeneticsDate: 2013-11-18"));
        addItem(new Item(53.5510846, 9.9936818, "PhD Research", "10.1080/02626667.2013.839875", "Researcher", "http://www.tandfonline.com/doi/abs/10.1080/02626667.2013.839875#.UonzBsSkqUb", "Nov 18, 2013", "Josep de Trincheria", "Title: Relationship between bedload and suspended sediment in the sand-bed Exu River, in the semi-arid region of BrazilAuthors: Jose Ramon B. Cantalice, Moacyr Cunha Filho, Borko D. Stosic, Victor Casimiro Piscoya, Sergio M. S. Guerra, Vijay P. SinghJournal: Hydrological Sciences JournalDate: 2013-11"));
        addItem(new Item(51.454513, -2.58791, "External request from non-academic.", "10.1080/02724634.2013.779922", "Student", "http://www.tandfonline.com/doi/abs/10.1080/02724634.2013.779922#.UooiFsTIYwB", "Nov 18, 2013", "Jon Tennant", "Title: The evolution of squamosal shape in ceratopsid dinosaurs (Dinosauria, Ornithischia)Authors: Leonardo Maiorino, Andrew A. Farke, Paolo Piras, Michael J. Ryan, Kevin M. Terris, Tassos KotsakisJournal: Journal of Vertebrate PaleontologyDate: 2013-11"));
        addItem(new Item(50.5, 4.8, "", "10.1105/tpc.113.118364", "Researcher", "http://www.plantcell.org/content/early/2013/11/12/tpc.113.118364.full.pdf+html", "Nov 18, 2013", "Guillaume Lobet", "Title: The Cold Signaling Attenuator HIGH EXPRESSION OF OSMOTICALLY RESPONSIVE GENE1 Activates FLOWERING LOCUS C Transcription via Chromatin Remodeling under Short-Term Cold Stress in ArabidopsisAuthors: J.-H. Jung, J.-H. Park, S. Lee, T. K. To, J.-M. Kim, M. Seki, C.-M. ParkJournal: The Plant CellDate: 2013-11-12"));
        addItem(new Item(51.5, -0.1, "I want to cite this in my PhD Thesis.", "10.1007/978-3-642-23099-8_11", "Student", "http://link.springer.com/chapter/10.1007/978-3-642-23099-8_11", "Nov 18, 2013", "Florian Rathgeber", "Title: FFC: the FEniCS form compilerAuthors: Anders Logg, Kristian B. Ălgaard, Marie E. Rognes, Garth N. WellsDate: 2012-2-4"));
        addItem(new Item(53.8, -1.6, "trying to write a bloody essay here!!", "10.1080/00034989858989", "Student", "http://www.researchgate.net/publication/13363754_Malaria_and_anaemia_at_different_altitudes_in_the_Muheza_district_of_Tanzania_childhood_morbidity_in_relation_to_level_of_exposure_to_infection", "Nov 18, 2013", "George Walker", "Title: Malaria and anaemia at different altitudes in the Muheza district of Tanzania: childhood morbidity in relation to level of exposure to infectionAuthors: R. ELLMAN C. MAXWELL R. FINCH D. SHAYOJournal: Annals of Tropical Medicine And ParasitologyDate: 1998-10-1"));
        addItem(new Item(53.8, -1.6, "", "10.1603/0022-2585-40.5.706", "Student", "http://www.bioone.org/doi/pdf/10.1603/0022-2585-40.5.706", "Nov 18, 2013", "George Walker", "Title: Relationship Between Altitude and Intensity of Malaria Transmission in the Usambara Mountains, TanzaniaAuthors: R. BĂ¸dker, J. Akida, D. Shayo, W. Kisinza, H. A. Msangeni, E. M. Pedersen, S. W. LindsayJournal: Journal of Medical EntomologyDate: 2003-9-1"));
        addItem(new Item(41.5800945, -71.4774291, "Trying to access published copies of my past work. ", "10.1080/01490410306704", "Researcher", "http://www.tandfonline.com/doi/pdf/10.1080/01490410306704#.Uoom7ZIjJ8E", "Nov 18, 2013", "Jeffrey W Hollister", "Title: Overview of GIS Applications in Estuarine Monitoring and Assessment ResearchAuthors: John F. Paul, Jane L. Copeland, Michael Charpentier, Peter V. August, Jeffrey W. HollisterJournal: Marine GeodesyDate: 2003-1"));
        addItem(new Item(51.5112139, -0.1198244, "", "10.1386/stic.4.2.251_1", "Academic", "http://www.intellectbooks.co.uk/journals/view-Article,id=16315/", "Nov 18, 2013", "Ernesto Priego", "Title: âAnimatingâ the narrative in abstract comicsAuthors: Paul Fisher DaviesJournal: Studies in ComicsDate: 2013-10-1"));
        addItem(new Item(35.0456297, -85.3096801, "I teach college students how to access and evaluate information and I want to stay abreast of the latest research.", "10.1017/epi.2013.43", "Librarian", "http://journals.cambridge.org/action/displayAbstract?fromPage=online&aid=9059856&fulltextType=RA&fileId=S1742360013000439", "Nov 18, 2013", "Lane Wilkinson", "Title: TRUSTWORTHINESS AND TRUTH: THE EPISTEMIC PITFALLS OF INTERNET ACCOUNTABILITYAuthors: Karen Frost-ArnoldJournal: EpistemeDate: 2013-10-29"));
        addItem(new Item(35.0456297, -85.3096801, "A library patron needs access for her graduate thesis. ", "10.1002/ss.37119874006", "Librarian", "http://onlinelibrary.wiley.com/doi/10.1002/ss.37119874006/abstract", "Nov 18, 2013", "Lane Wilkinson", "Title: Defining the relationship between fraternities and sororities and the host institutionAuthors: Terrence E. Milani, William R. NettlesJournal: New Directions for Student ServicesDate: 1987-24"));
        addItem(new Item(51.5112139, -0.1198244, "Because this is an article about sharing public databases. A bit of a contradiction? ", "10.1038/4611053b", "Academic", "http://www.nature.com/nature/journal/v461/n7267/full/4611053b.html", "Nov 18, 2013", "Ernesto Priego", "Title: Sharing: public databases combat mistrust and secrecyAuthors: Andrew A. Farke, Michael P. Taylor, Mathew J. WedelJournal: NatureDate: 2009-10-22"));
        addItem(new Item(39.5, -87.4, "curious", "10.1007/s00202-012-0265-3", "Librarian", "http://link.springer.com/article/10.1007/s00202-012-0265-3", "Nov 19, 2013", "Rich B", "Title: External rotor SRM with high torque per volume: design, analysis, and experimentsAuthors: H. Torkaman, E. Afjei, A. Gorgani, N. Faraji, H. Karim, N. ArbabJournal: Electrical EngineeringDate: 2013-12-22"));
        addItem(new Item(31.9685988, -99.9018131, "", "", "Librarian", "http://digitalcommons.trinity.edu/physics_faculty/25/", "Nov 19, 2013", "Jane Costanza", "multispacecraft ISTP Study"));
        addItem(new Item(31.9685988, -99.9018131, "", "", "Librarian", "http://pwg.gsfc.nasa.gov/istp/newsletters/V6N3/newsletter.html#ANCH4", "Nov 19, 2013", "Jane Costanza", "multispacecraft ISTP Study"));
        addItem(new Item(38.9, -77.0, "librarian for nonprofit", "", "Librarian", "https://mail.google.com/mail/u/0/?shva=1#inbox", "Nov 19, 2013", "Laurie Calhoun", ""));
        addItem(new Item(37.4, -6.1, "", "", "Researcher", "http://www.openaccessbutton.org/#top", "Nov 19, 2013", "Mercedes González", ""));
        addItem(new Item(-23.5489433, -46.6388182, "Understanding role of developmentalist policies in left-leaning governments in Latin America.", "10.1126/science.1244693", "Academic", "http://www.sciencemag.org/content/342/6160/850", "Nov 18, 2013", "Miguel Said Vieira", "Title: High-Resolution Global Maps of 21st-Century Forest Cover ChangeAuthors: M. C. Hansen, P. V. Potapov, R. Moore, M. Hancher, S. A. Turubanova, A. Tyukavina, D. Thau, S. V. Stehman, S. J. Goetz, T. R. Loveland, A. Kommareddy, A. Egorov, L. Chini, C. O. Justice, J. R. G. TownshendJournal: ScienceDate: 2013-11-14"));
        addItem(new Item(51.5112139, -0.1198244, "", "10.1016/0895-4356(94)90103-1", "Academic", "http://www.sciencedirect.com/science/article/pii/0895435694901031", "Nov 18, 2013", "Ernesto Priego", "Title: Are public library books contaminated by bacteria?Authors: Sara J. Brook, Itzhak BrookJournal: Journal of Clinical EpidemiologyDate: 1994-10"));
        addItem(new Item(51.6, -0.1, "I use libraries! Am I safe?", "10.1016/0895-4356(94)90103-1", "Student", "http://www.sciencedirect.com/science/article/pii/0895435694901031", "Nov 18, 2013", "John Levin", "Title: Are public library books contaminated by bacteria?Authors: Sara J. Brook, Itzhak BrookJournal: Journal of Clinical EpidemiologyDate: 1994-10"));
        addItem(new Item(56.130366, -106.346771, "drug side effects", "10.1016/j.brainres.2013.11.007", "Patient", "http://www.sciencedirect.com/science/article/pii/S0006899313015023", "Nov 18, 2013", "Catherine Warren", "Title: Hormonal contraceptives masculinize brain activation patterns in the absence of behavioral changes in two numerical tasksAuthors: Pletzer Belinda, Kronbichler Martin, Nuerk Hans-Christoph, Kerschbaum HubertJournal: Brain ResearchDate: 2013-11"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliogrphy.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliogrphy.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliogrphy.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliogrphy.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliogrphy.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliogrphy.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliogrphy.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(1.352083, 103.819836, "Need it for a complete bibliography.", "10.1629/2048-7754.75", "Librarian", "https://uksg.metapress.com/content/b771618u5261r434/resource-secured/?target=fulltext.html", "Nov 18, 2013", "Aaron Tay", "Library technology in content discovery – evidence from a large-scale reader survey, 	Simon Inger and Tracy Gardner, Based on a paper presented at the 36th UKSG Annual Conference, Bournemouth, April 2013"));
        addItem(new Item(45.2, 5.8, "To keep up with research in my field", "10.1080/09502386.2012.730542", "Academic", "http://www.tandfonline.com/doi/abs/10.1080/09502386.2012.730542#.UoqB3ZHmbdE", "Nov 19, 2013", "Myriam Houssay-Holzschuch", "Title: Public crises, public futuresAuthors: Nick Mahony, John ClarkeJournal: Cultural StudiesDate: 2013-7"));
        addItem(new Item(34.0966764, -117.7197785, "research on horned dinosaurs", "10.1007/s12549-013-0123-y", "Researcher", "http://link.springer.com/article/10.1007/s12549-013-0123-y", "Nov 19, 2013", "Andrew Farke", "Title: Did fire play a role in formation of dinosaur-rich deposits? An example from the Late Cretaceous of CanadaAuthors: Sarah A. E. Brown, Margaret E. Collinson, Andrew C. ScottJournal: Palaeobiodiversity and PalaeoenvironmentsDate: 2013-9-22"));
        addItem(new Item(52.5, 13.4, "Research", "", "Librarian", "http://psycnet.apa.org/index.cfm?fa=buy.optionToBuy&id=2002-15790-003", "Nov 19, 2013", "Daniel Payne", "By Locke, Edwin A.; Latham, Gary P.American Psychologist, Vol 57(9), Sep 2002, 705-717."));
        addItem(new Item(45.5086699, -73.5539925, "Give access to the results of a literature search for a health care professional.", "10.1002/bjs.1800711202", "Librarian", "http://onlinelibrary.wiley.com/doi/10.1002/bjs.1800711202/pdf", "Nov 19, 2013", "Jacynthe Touchette", "Title: Emergency surgery for diverticular disease complicated by generalized and faecal peritonitis: A reviewAuthors: Z. H. Krukowski, N. A. MathesonJournal: British Journal of SurgeryDate: 1984-12"));
        addItem(new Item(33.1, -117.0, "", "", "Student", "https://www.openaccessbutton.org/#top", "Nov 20, 2013", "Sang Shin", ""));
        addItem(new Item(32.3512601, -95.3010624, "I'm looking for solutions for The University of Texas at Tyler library to continue providing access to books for students in East Texas. This article about remote storage would be helpful to me in decision making.", "10.1080/01462679.2013.841603", "Librarian", "http://www.tandfonline.com/doi/full/10.1080/01462679.2013.841603#.Uoo6DcRebTo", "Nov 18, 2013", "Tiffany LeMaistre", "Title: Building Faculty Support for Remote Storage: A Survey of Collection Behaviors and PreferencesAuthors: Eunice Schroeder, Janet Martorana, Chris GranatinoJournal: Collection ManagementDate: 2013-10"));
        addItem(new Item(53.381129, -1.470085, "", "10.1038/nrd2829", "Student", "http://www.nature.com/nrd/journal/v8/n6/full/nrd2829.html", "Nov 18, 2013", "Andrew Nichols", "Title: Targeting innate immunity protein kinase signalling in inflammationAuthors: Matthias Gaestel, Alexey Kotlyarov, Michael KrachtJournal: Nature Reviews Drug DiscoveryDate: 2009-6"));
        addItem(new Item(50.6325574, 5.5796662, "", "10.1080/0361526X.2013.836465", "Librarian", "http://www.tandfonline.com/doi/pdf/10.1080/0361526X.2013.836465#.Uoo7oOKqko4", "Nov 18, 2013", "Cecile Dohogne", "Title: Description of Serials, RDA, and the MARC 21 Bibliographic FormatAuthors: Ed JonesJournal: The Serials LibrarianDate: 2013-11"));
        addItem(new Item(54.978252, -1.61778, "Working on emotional deprivation and links to undernutrition in low-income settings.", "10.1177/0165025407074634", "Academic", "http://jbd.sagepub.com/content/31/3/218.full.pdf+html", "Nov 18, 2013", "Sunil Bhopal", "Title: Associations between parents' marital functioning, maternal parenting quality, maternal emotion and child cortisol levelsAuthors: P. Pendry, E. K. AdamJournal: International Journal of Behavioral DevelopmentDate: 2007-5-1"));
        addItem(new Item(40.7143528, -74.0059731, "Interested in learning more about this topic!", "10.1021/ja4045289", "Student", "http://pubs.acs.org/doi/abs/10.1021/ja4045289", "Nov 18, 2013", "Steve", "Methane Storage in Metal–Organic Frameworks: Current Records, Surprise Findings, and Challenges, Yang Peng †‡, Vaiva Krungleviciute †‡, Ibrahim Eryazici §, Joseph T. Hupp §, Omar K. Farha *§, and Taner Yildirim *†‡, journal of american chemical society"));
        addItem(new Item(38.9, -77.0, "I want to share the publicly-released video, which referred incompletely to a figure in the closed-access journal article. I don't want to share the video without checking the facts.", "10.1001/jama.2013.281425", "Researcher", "http://jama.jamanetwork.com/article.aspx?articleid=1769890", "Nov 18, 2013", "Susannah Fox", "Title: The Anatomy of Health Care in the United StatesAuthors: Hamilton Moses, David H. M. Matheson, E. Ray Dorsey, Benjamin P. George, David Sadoff, Satoshi YoshimuraJournal: JAMADate: 2013-11-13"));
        addItem(new Item(44.2311717, -76.4859544, "Provided a link on twitter on social media in public health promotion campaigns.  Unfortunately I could not access it on my mobile device due to the paywall.", "10.1177/1524839911433467", "Other", "http://m.hpp.sagepub.com/content/13/2/159.short", "Nov 18, 2013", "Ian Pereira", "Title: Use of Social Media in Health Promotion: Purposes, Key Performance Indicators, and Evaluation MetricsAuthors: B. L. Neiger, R. Thackeray, S. A. Van Wagenen, C. L. Hanson, J. H. West, M. D. Barnes, M. C. FagenJournal: Health Promotion PracticeDate: 2012-3-1"));
        addItem(new Item(53.801279, -1.548567, "", "10.1016/S0140-6736(96)91488-9", "Student", "http://www.thelancet.com/journals/lancet/article/PIIS0140-6736(96)91488-9/abstract", "Nov 18, 2013", "George Walker", "Title: Effects of artemisinin derivatives on malaria transmissibilityAuthors: R.N Price, F Nosten, C Luxemburger, F.O ter Kuile, L Paiphun, R.N Price, C Luxemburger, T Chongsuphajaisiddhi, N.J White, R.N Price, F Nosten, N.J White, F.O ter KuileJournal: The LancetDate: 1996-6"));
        addItem(new Item(51.62144, -3.943646, "We have a reading list system and reading how others have evaluated would be very helpful to our university library service.", "10.1177/1474022212460743", "Librarian", "http://www.scopus.com/record/display.url?eid=2-s2.0-84886919923&origin=SingleRecordEmailAlert&txGid=5825371F9518D75D382F63840E36099D.WXhD7YyTQ6A7Pvk9AlA%3a1", "Nov 18, 2013", "Sam Oakley", "Title: Understanding arts and humanities students' experiences of assessment and feedbackAuthors: J. Adams, N. McNabJournal: Arts and Humanities in Higher EducationDate: 2013-1-24"));
        addItem(new Item(53.801279, -1.548567, "", "10.1056/NEJMoa0808859", "Student", "http://www.nejm.org/doi/full/10.1056/nejmoa0808859#Top", "Nov 18, 2013", "George Walker", "Title:               Artemisinin Resistance in              Plasmodium falciparum              Malaria            Authors: Arjen M. Dondorp, Franรงois Nosten, Poravuth Yi, Debashish Das, Aung Phae Phyo, Joel Tarning, Khin Maung Lwin, Frederic Ariey, Warunee Hanpithakpong, Sue J. Lee, Pascal Ringwald, Kamolrat Silamut, Mallika Imwong, Kesinee Chotivanich, Pharath Lim, Trent Herdman, Sen Sam An, Shunmay Yeung, Pratap Singhasivanon, Nicholas P.J. Day, Niklas Lindegardh, Duong Socheat, Nicholas J. WhiteJournal: New England Journal of MedicineDate: 2009-7-30"));
        addItem(new Item(51.5, -0.1, "Just curious", "10.3111/13696998.2011.637590", "Other", "http://informahealthcare.com/doi/pdf/10.3111/13696998.2011.637590", "Nov 18, 2013", "Lucy Abel", "Title: Cost-effectiveness of prasugrel in a US managed care populationAuthors: Josephine A. Mauskopf, Jonathan B. Graham, Jay P. Bae, Krishnan Ramaswamy, Anthony J. Zagar, Elizabeth A. Magnuson, David J. Cohen, Eric S. MeadowsJournal: Journal of Medical EconomicsDate: 2012-2"));
        addItem(new Item(53.801279, -1.548567, "", "10.1016/S0140-6736(05)67176-0", "Student", "http://www.thelancet.com/journals/lancet/article/PIIS0140-6736(05)67176-0/fulltext", "Nov 18, 2013", "George Walker", "Title: Artesunate versus quinine for treatment of severe falciparum malaria: a randomised trialAuthors: Journal: The LancetDate: 2005-8"));
        addItem(new Item(47.4179284, 9.3643968, "Input/material for teaching IS class in eBiz", "10.1016/j.intmar.2013.09.008", "Academic", "http://www.sciencedirect.com/science/article/pii/S1094996813000431", "Nov 18, 2013", "Hans-Dieter Zimmermann", "Title: Managing Customer Relationships in the Social Media Era: Introducing the Social CRM HouseAuthors: Edward C. Malthouse, Michael Haenlein, Bernd Skiera, Egbert Wege, Michael ZhangJournal: Journal of Interactive MarketingDate: 2013-10"));
        addItem(new Item(38.5449065, -121.7405167, "Seeking access to evaluate risks for the adjunctive use of potassium bromide for the treatment of epilepsy in the dog.", "10.2460/javma.240.9.1055", "Doctor", "http://avmajournals.avma.org/doi/pdf/10.2460/javma.240.9.1055", "Nov 18, 2013", "Stuart Turner", "Title: Letters to the EditorAuthors: Journal: Journal of the American Veterinary Medical AssociationDate: 2012-5"));
        addItem(new Item(51.7, -2.2, "To help with my research into biomimetic adhesive particles, which could have applications in targeted drug delivery and air filtration systems.", "10.1117/12.2017434", "Student", "http://proceedings.spiedigitallibrary.org/proceeding.aspx?articleid=1689817", "Nov 18, 2013", "Alex Hughes-Games", "Title: Efficient nanoparticle filtering using bioinspired functional sufacesAuthors: Sebastian Busch, Manuel Ketterer, Xenia Vinzenz, Christian Hoffmann, Jürgen WöllensteinJournal: Smart Sensors, Actuators, and MEMS VIDate: 2013-5-17"));
        addItem(new Item(51.6, -0.1, "", "10.3138/chr.694", "Student", "https://utpjournals.metapress.com/content/k002j61230g4556w/resource-secured/?target=fulltext.pdf", "Nov 18, 2013", "John Levin", "Title: Illusionary Order: Online Databases, Optical Character Recognition, and Canadian History, 1997â2010Authors: Ian MilliganJournal: Canadian Historical ReviewDate: 2013-12-1"));
        addItem(new Item(50.9097004, -1.4043509, "Research for a study helping paediatric patients with CKD.", "10.1093/ndt/gfs557", "Student", "http://ndt.oxfordjournals.org/content/28/4/821", "Nov 18, 2013", "Harrison Stubbs", "Title: FGF23 antagonism: the thin line between adaptation and maladaptation in chronic kidney diseaseAuthors: M. Ketteler, P. H. Biggar, O. LiangosJournal: Nephrology Dialysis TransplantationDate: 2013-3-29"));
        addItem(new Item(50.9097004, -1.4043509, "Research for a study helping paediatric patients with CKD.", "10.1093/ndt/gft065", "Student", "http://ndt.oxfordjournals.org/content/28/9/2228", "Nov 18, 2013", "Harrison Stubbs", "Title: Fibroblast growth factor-23: what we know, what we don't know, and what we need to knowAuthors: C. P. Kovesdy, L. D. QuarlesJournal: Nephrology Dialysis TransplantationDate: 2013-9-11"));
        addItem(new Item(50.9097004, -1.4043509, "Research for a study helping paediatric patients with CKD.", "10.1097/MNH.0b013e32836213ee", "Student", "http://journals.lww.com/co-nephrolhypertens/Abstract/2013/07000/FGF23_and_Klotho_in_chronic_kidney_disease.7.aspx", "Nov 18, 2013", "Harrison Stubbs", "Title: FGF23 and Klotho in chronic kidney diseaseAuthors: Hannes Olauson, Tobias E. LarssonJournal: Current Opinion in Nephrology and HypertensionDate: 2013-7"));
        addItem(new Item(33.7489954, -84.3879824, "", "10.1177/097194581201500208", "Librarian", "http://mhj.sagepub.com/content/15/2/415.full.pdf+html", "Nov 18, 2013", "Fred Rascoe", "Title: Objects, Frames, Practices: A Postscript on Agency and Braided Histories of ArtAuthors: M. JunejaJournal: The Medieval History JournalDate: 2013-3-20"));
        addItem(new Item(49.1658836, -123.9400647, "Helping a student with a project", "", "Librarian", "http://www.editlib.org/p/51370/", "Nov 19, 2013", "Dana McFarland", "Dexter, S. (2011). School Technology Leadership: Artifacts in Systems of Practice. Journal of School Leadership, 21(2), 166-189. Retrieved November 18, 2013 from http://www.editlib.org/p/51370."));
        addItem(new Item(32.7, -117.2, "research", "", "Student", "http://journals.humankinetics.com/ijsnem-current-issue/ijsnem-volume-23-issue-5-october/a-comparison-of-caffeine-versus-pseudoephedrine-on-cycling-time-trial-performance", "Nov 19, 2013", "Emilie Reas", "A Comparison of Caffeine Versus Pseudoephedrine on Cycling Time-Trial PerformanceSpence, Sim, Landers, PeelingIJSNEM"));
        addItem(new Item(38.5449065, -121.7405167, "requested by a researcher", "10.1016/j.cden.2013.01.002", "Librarian", "http://www.sciencedirect.com/science/article/pii/S0011853213000037", "Nov 19, 2013", "Phoebe Ayers", ""));
        addItem(new Item(39.952335, -75.163789, "testing", "10.1016/j.jvoice.2012.06.005", "Librarian", "http://www.sciencedirect.com/science/article/pii/S0892199712000975", "Nov 19, 2013", "Richard James", "Title: Obstacles to Communication in Children With Cri du Chat SyndromeAuthors: Jordan M. Virbalas, Gina Palma, Melin TanJournal: Journal of VoiceDate: 2012-11"));
        addItem(new Item(56.130366, -106.346771, "learn more about a symptom I have", "10.1111/eci.12201", "Patient", "http://onlinelibrary.wiley.com/doi/10.1111/eci.12201/pdf", "Nov 19, 2013", "Catherine Warren", "Title: Recovery of upper limb muscle function in chronic fatigue syndromeAuthors: Kelly Ickmans, Mira Meeus, Margot De Kooning, Luc Lambrecht, Jo NijsJournal: European Journal of Clinical InvestigationDate: 2013-11"));
        addItem(new Item(41.3850639, 2.1734035, "", "", "Doctor", "http://www.uoc.edu/uocpapers/3/dt/eng/tramullas_garrido.html", "Nov 19, 2013", "Jesus Tramullas", ""));
        addItem(new Item(41.3850639, 2.1734035, "", "", "Doctor", "http://www.elprofesionaldelainformacion.com/contenidos/2013/sept/07.html", "Nov 19, 2013", "Jesus Tramullas", ""));
        addItem(new Item(41.3850639, 2.1734035, "", "", "Doctor", "http://www.elprofesionaldelainformacion.com/contenidos/2013/sept/07.html", "Nov 19, 2013", "Jesus Tramullas", ""));
        addItem(new Item(41.3850639, 2.1734035, "", "", "Doctor", "http://elprofesionaldelainformacion.metapress.com/app/home/contribution.asp?referrer=parent&backto=issue,7,13;journal,2,92;linkingpublicationresults,1:105302,1", "Nov 19, 2013", "Jesus Tramullas", "Gestión de contenidos con Drupal: revisión de módulos específicos para bibliotecas, archivos y museosJesús Tramullas"));
        addItem(new Item(55.604981, 13.003822, "Enhancing nutricious foods for several thousands refugees throughout the world", "10.1007/s002170000200", "Librarian", "http://link.springer.com/article/10.1007/s002170000200", "Nov 19, 2013", "Andreas Jonsson", "Title: Effects of sprouting on nutrient and antinutrient composition of kidney beans (Phaseolus vulgaris var. Rose coco)Authors: Stephen Mbithi, John Van Camp, Raquel Rodriguez, Andre HuyghebaertJournal: European Food Research and TechnologyDate: 2001-1-17"));
        addItem(new Item(25.7, -100.3, "For a homework in my Metabolic engineering class", "", "Student", "http://www.sciencedirect.com/science/article/pii/B9780124047419000015", "Nov 19, 2013", "Maria Luisa Marcos Sanchez", "Dicer: Structure, Function And Role In RNA-Dependent Gene-Silencing PathwaysJustin M. Pare, Tom C. Hobman Industrial Enzymes2007, pp 421-438 "));
        addItem(new Item(13.0, 77.6, "", "", "Undisclosed", "http://www.openaccessbutton.org/#top", "Nov 19, 2013", "Vignesh Prabhu", "John L sullivan"));
        addItem(new Item(31.9685988, -99.9018131, "", "", "Librarian", "http://digitalcommons.trinity.edu/bio_faculty/15/", "Nov 19, 2013", "Jane Costanza", "It isn't always sexy when both are bright and shiny"));
        addItem(new Item(40.2671941, -86.1349019, "", "10.2478/s11696-013-0439-0", "Librarian", "http://link.springer.com/article/10.2478/s11696-013-0439-0", "Nov 19, 2013", "Rich B", "Title: Polycarbonate-based polyurethane elastomers: temperature-dependence of tensile propertiesAuthors: Zdeněk Hrdlička, Antonín Kuta, Rafał Poręba, Milena ŠpírkováJournal: Chemical PapersDate: 2014-2-6"));
        addItem(new Item(51.454513, -2.58791, "Essay on health policy and health care systems finance", "10.1016/S0277-9536(99)00150-1", "Student", "http://www.sciencedirect.com/science/article/pii/S0277953699001501", "Nov 19, 2013", "Avgi Loizidou", "Title: The impact of public spending on health: does money matter?Authors: Deon Filmer, Lant PritchettJournal: Social Science & MedicineDate: 1999-11"));
        addItem(new Item(46.7312745, -117.1796158, "academic research ", "10.1300/J122v19n02_02", "Librarian", "http://www.tandfonline.com/doi/pdf/10.1300/J122v19n02_02#.UoqJDPmsh8E", "Nov 19, 2013", "Chelsea Leachman", "Title: Academic Science and Engineering LibrariansAuthors: Mark D. WinstonJournal: Science & Technology LibrariesDate: 2000-12"));
        addItem(new Item(39.5, -87.4, "", "", "Librarian", "http://www.rose-hulman.edu/offices-services/logan-library.aspx", "Nov 19, 2013", "Rich B", ""));
        addItem(new Item(-27.6, 153.1, "Wanting to help lecturers find OA information for their online courses", "", "Librarian", "http://www.openaccessbutton.org/#top", "Nov 19, 2013", "Susan Tegg", ""));
        addItem(new Item(60.3912628, 5.3220544, "I'm curious about Google Scholar metrics.", "10.1002/asi.23056", "Student", "http://onlinelibrary.wiley.com/doi/10.1002/asi.23056/pdf", "Nov 19, 2013", "Bruno Cossermelli Vellutini", "Title: The Google scholar experiment: How to index false papers and manipulate bibliometric indicatorsAuthors: Emilio Delgado López-Cózar, Nicolás Robinson-García, Daniel Torres-SalinasJournal: Journal of the American Society for Information Science and TechnologyDate: 2013-12-11"));
        addItem(new Item(41.3850639, 2.1734035, "For my study", "", "Other", "http://www.elprofesionaldelainformacion.com/contenidos/2013/noviembre/08.html", "Nov 19, 2013", "Tomàs Baiget", "Rich snippets: información semántica para la mejora de la identidad digital y el SEO, Cristòfol Rovira, Lluís Codina, Ricard Monistrol, El profesional de la informacion"));
        addItem(new Item(51.454513, -2.58791, "Primary research into the Jurassic/Cretaceous boundary. Important too, right..?", "10.1111/j.1095-8312.1988.tb00476.x", "Student", "http://onlinelibrary.wiley.com/doi/10.1111/j.1095-8312.1988.tb00476.x/abstract", "Nov 19, 2013", "Jon Tennant", "Title: Evolutionary patterns of host utilization by ichneumonoid parasitoids (Hymenoptera: Ichneumonidae and Braconidae)*Authors: IAN D. GAULDJournal: Biological Journal of the Linnean SocietyDate: 1988-12-14"));
        addItem(new Item(44.2311717, -76.4859544, "Comparing Canadian drivers of physician wellbeing to those in other countries for healthier doctors, for healthier patients.", "10.1007/s00420-011-0725-5", "Other", "http://link.springer.com/article/10.1007%2Fs00420-011-0725-5", "Nov 19, 2013", "Ian Pereira", "Title: Job stress and job satisfaction of physicians in private practice: comparison of German and Norwegian physiciansAuthors: Edgar Voltmer, Judith Rosta, Johannes Siegrist, Olaf G. AaslandJournal: International Archives of Occupational and Environmental HealthDate: 2012-10-11"));
        addItem(new Item(-27.4710107, 153.0234489, "Needed for scientific research", "10.1111/j.1420-9101.2010.02216.x", "Librarian", "http://onlinelibrary.wiley.com/doi/10.1111/j.1420-9101.2010.02216.x/abstract", "Nov 19, 2013", "Susan Tegg", "Title: Temporal patterns of diversification in Andean Eois, a species-rich clade of moths (Lepidoptera, Geometridae)Authors: P. STRUTZENBERGER, K. FIEDLERJournal: Journal of Evolutionary BiologyDate: 2011-4-24"));
        addItem(new Item(-27.4710107, 153.0234489, "testing button", "", "Librarian", "https://www104.griffith.edu.au/index.php/dancecult/article/view/353", "Nov 19, 2013", "Susan Tegg", ""));
        addItem(new Item(-27.6, 153.1, "testing button", "", "Librarian", "https://www104.griffith.edu.au/index.php/dancecult/article/view/353", "Nov 19, 2013", "Susan Tegg", ""));
        addItem(new Item(-27.6, 153.1, "testing button", "", "Librarian", "https://www104.griffith.edu.au/index.php/dancecult/article/view/353", "Nov 19, 2013", "Susan Tegg", ""));
        addItem(new Item(-27.6, 153.1, "", "", "Librarian", "https://www104.griffith.edu.au/index.php/dancecult/article/view/353", "Nov 19, 2013", "Susan Tegg", ""));
        addItem(new Item(-27.6, 153.1, "", "", "Librarian", "https://www104.griffith.edu.au/index.php/dancecult/article/view/353", "Nov 19, 2013", "Susan Tegg", ""));
        addItem(new Item(48.4284207, -123.3656444, "I want to see what research has already been done into the area of open textbooks.", "10.1080/02680513.2011.538563", "Student", "http://www.tandfonline.com/doi/abs/10.1080/02680513.2011.538563#.UoqZqGRVDHh", "Nov 19, 2013", "Mary Burgess", "Open Textbook adoption and use: implications for teachers and learners"));
        addItem(new Item(61.4981508, 23.7610254, "I wanted to analyze this article as part of my academic english language course. The research is done in my own department at Tampere University", "10.1016/j.elerap.2013.01.004", "Student", "http://www.sciencedirect.com/science/article/pii/S1567422313000112", "Nov 19, 2013", "Mace Ojala", "Title: Transforming homo economicus into homo ludens: A field experiment on gamification in a utilitarian peer-to-peer trading serviceAuthors: Juho HamariJournal: Electronic Commerce Research and ApplicationsDate: 2013-7"));
        addItem(new Item(-35.3, 149.1, "", "10.1142/S0218216597000248", "Academic", "http://www.worldscientific.com/doi/pdf/10.1142/S0218216597000248", "Nov 20, 2013", "Scott Morrison", "Title: The Third Derivative of the Jones PolynomialAuthors: Yasuyuki MiyazawaJournal: Journal of Knot Theory and Its RamificationsDate: 1997-6"));
        addItem(new Item(37.7, 126.8, "", "10.1038/nature12671", "Undisclosed", "http://www.nature.com/nature/journal/v503/n7475/full/nature12671.html", "Nov 20, 2013", "Nolboo Kim", "Title: The trajectory, structure and origin of the Chelyabinsk asteroidal impactorAuthors: Jiří Borovička, Pavel Spurný, Peter Brown, Paul Wiegert, Pavel Kalenda, David Clark, Lukáš ShrbenýJournal: NatureDate: 2013-11-6"));
        addItem(new Item(6.4, 5.6, "Research materials", "", "Student", "https://www.openaccessbutton.org/#top", "Nov 20, 2013", "Mirabel Akure", "Journals"));
        addItem(new Item(36.4, 127.4, "", "", "Researcher", "https://www.openaccessbutton.org/#top", "Nov 21, 2013", "Youngchan Shin", ""));
        addItem(new Item(43.8041334, -120.5542012, "Improve clinical accuracy in identifying a SLAP lesion for a new patient.", "10.1016/j.ptsp.2008.07.001", "Doctor", "http://www.sciencedirect.com/science/article/pii/S1466853X08000771", "Nov 19, 2013", "Jason Harris", "Title: Identifying SLAP lesions: A meta-analysis of clinical tests and exercise in clinical reasoningAuthors: David M. Walton, Jackie SadiJournal: Physical Therapy in SportDate: 2008-11"));
        addItem(new Item(45.5945645, -121.1786823, "Attempting to improve clinical diagnosis for new patient evaluation.", "10.1016/j.ptsp.2008.07.001", "Doctor", "http://www.physicaltherapyinsport.com/article/S1466-853X(08)00077-1/abstract", "Nov 19, 2013", "Jason Harris", "Title: Identifying SLAP lesions: A meta-analysis of clinical tests and exercise in clinical reasoningAuthors: David M. Walton, Jackie SadiJournal: Physical Therapy in SportDate: 2008-11"));
        addItem(new Item(50.9, 6.9, "I'm trying to read a paper on medical education in order to understand how and why we study to become health advocates.", "10.1111/medu.12229", "Student", "http://onlinelibrary.wiley.com/doi/10.1111/medu.12229/pdf", "Nov 19, 2013", "Hormos Dafsari", "Title: On the role of biomedical knowledge in the acquisition of clinical knowledgeAuthors: Stefan K Schauber, Martin Hecht, Zineb M Nouns, Susanne DettmerJournal: Medical EducationDate: 2013-12-11"));
        addItem(new Item(38.9072309, -77.0364641, "I'm in science education (curriculum development/research/writing/editing) and need to keep up with relevant literature. ", "10.1080/09500693.2012.683461", "Other", "http://www.tandfonline.com/doi/pdf/10.1080/09500693.2012.683461#.UoqhJ2SxORg", "Nov 19, 2013", "Jean Flanagan", "Title: Stereotype Threat and Women's Performance in PhysicsAuthors: Gwen C. Marchand, Gita TaasoobshiraziJournal: International Journal of Science EducationDate: 2013-12"));
        addItem(new Item(41.4, 2.2, "", "10.1093/heapro/5.1.19", "Student", "http://heapro.oxfordjournals.org/content/5/1/19.full.pdf+html", "Nov 19, 2013", "Sherman Kong", "Title: Risk factor loneliness. On the interrelations between social integration, happiness and health in 11-, 13- and 15-year old schoolchildren in 9 European countriesAuthors: ANSELM EDERJournal: Health Promotion InternationalDate: 1990"));
        addItem(new Item(54.6, -5.9, "Reading for medicine course", "", "Student", "http://www.minervamedica.it/en/journals/minerva-pediatrica/article.php?cod=R15Y2013N06A0587&acquista=1", "Nov 19, 2013", "Lucy Loughrey", "Minerva Pediatrica 2013 December;65(6):587-98"));
        addItem(new Item(44.0, -79.5, "I'm a patient with complications and want to find out more information.", "10.1016/j.urology.2010.05.009", "Advocate", "https://www.ncbi.nlm.nih.gov/pubmed/20709373", "Nov 19, 2013", "Victor Ng", "Title: Efficacy of Epididymectomy in Treatment of Chronic Epididymal Pain: A Comparison of Patients With and Without a History of VasectomyAuthors: Joo Yong Lee, Tchun Yong Lee, Hae Young Park, Hong Yong Choi, Tag Keun Yoo, Hong Sang Moon, June Hyun Han, Sung Yul Park, Seung Wook LeeJournal: UrologyDate: 2011-1"));
        addItem(new Item(51.053468, 3.73038, "", "10.1007/978-3-540-30192-9_8", "Researcher", "http://link.springer.com/chapter/10.1007/978-3-540-30192-9_8", "Nov 19, 2013", "Pieter C", "Title: Semantic Web Recommender SystemsAuthors: Cai-Nicolas ZieglerDate: 2004"));
        addItem(new Item(45.0598203, -92.9478335, "Presentation", "10.1016/S0733-8619(05)70368-6", "Student", "http://www.sciencedirect.com/science/article/pii/S0733861905703686", "Nov 19, 2013", "James Zhang", "Title: COMPLICATIONS OF LUMBAR PUNCTUREAuthors: Randolph W. EvansJournal: Neurologic ClinicsDate: 1998-2"));
        addItem(new Item(40.806862, -96.681679, "Engaged in biodiversity surveys in national parks and preserves and need descriptive articles to help identify specimens.", "10.1163/004425996X00038", "Researcher", "http://www.ingentaconnect.com/content/brill/nem/1996/00000042/00000003/art00003", "Nov 19, 2013", "Peter Mullin", "Title: Revision of the Genus Tylencholaimus De Man, 1876 Prodelphic Species: Part IvAuthors: A. Coomans, R. PeĂąa SantiagoJournal: NematologicaDate: 1996-1-1"));
        addItem(new Item(35.7595731, -79.0192997, "", "10.1080/2154896X.2013.783280", "Librarian", "http://www.tandfonline.com/doi/abs/10.1080/2154896X.2013.783280?journalCode=rpol20#.Uoq_b9Lkv2i", "Nov 19, 2013", "Kyle Denlinger", "Title: The politics of the âNew Northâ: putting history and geography at stake in Arctic futuresAuthors: Andrew StuhlJournal: The Polar JournalDate: 2013-6"));
        addItem(new Item(44.0, -79.5, "Patient advocate. Looking for more data.", "10.1016/j.urology.2008.02.036", "Advocate", "https://www.ncbi.nlm.nih.gov/pubmed/18436286", "Nov 19, 2013", "Victor Ng", "Title: Does Surgery Have a Role in Management of Chronic Intrascrotal Pain?Authors: Clare A. Sweeney, Grenville M. Oades, Michael Fraser, Michael PalmerJournal: UrologyDate: 2008-6"));
        addItem(new Item(28.5, 77.3, "For my class lecture and research on quantification turn in social sciences.", "10.1177/000312240907400104", "Academic", "http://asr.sagepub.com/content/74/1/63", "Nov 19, 2013", "Swagato Sarkar", "Title: The Discipline of Rankings: Tight Coupling and Organizational ChangeAuthors: M. Sauder, W. N. EspelandJournal: American Sociological ReviewDate: 2009-2-1"));
        addItem(new Item(40.806862, -96.681679, "Engaged in biodiversity surveys in national parks and preserves, need descriptive literature to make IDs of specimens.", "10.1080/00222933.2012.724722", "Researcher", "http://www.tandfonline.com/doi/pdf/10.1080/00222933.2012.724722#.UorHkY0myeM", "Nov 19, 2013", "Peter Mullin", "Title:               Four new and six known species of the genus              Dorylaimellus              Cobb, 1913 (Nematoda: Belondiridae) from India            Authors: Wasim Ahmad, Tabbasam NazJournal: Journal of Natural HistoryDate: 2012-12"));
        addItem(new Item(1.352083, 103.819836, "", "", "Librarian", "https://www.google.com/webhp?sourceid=chrome-instant&espv=210&ie=UTF-8", "Nov 19, 2013", "Aaron Tay", "test,test,test"));
        addItem(new Item(1.352083, 103.819836, "trying", "", "Librarian", "https://www.google.com/webhp?sourceid=chrome-instant&espv=210&ie=UTF-8", "Nov 19, 2013", "Aaron Tay", "test,test,test"));
        addItem(new Item(43.3255196, -79.7990319, "Medical school research", "10.1136/bmj.f6340", "Student", "http://www.bmj.com/content/347/bmj.f6340", "Nov 19, 2013", "Michael Forrester", "Title: Saturated fat is not the major issueAuthors: A. MalhotraJournal: BMJDate: 2013-10-22"));
        addItem(new Item(32.7153292, -117.1572551, "research", "", "Advocate", "http://online.wsj.com/news/articles/SB10001424052702303559504579196370226844190", "Nov 19, 2013", "Ron Mader", "wall street journal"));
        addItem(new Item(-28.0172605, 153.4256987, "I'm testing openaccess button.", "", "Librarian", "http://my.safaribooksonline.com/book/programming/javascript/9780132735483/firstchapter", "Nov 19, 2013", "Peta Hopkins", "Javascript: visual quickstart guide 8th ed"));
        addItem(new Item(-36.8484597, 174.7633315, "", "10.1111/j.1365-2672.1979.tb02580.x", "Researcher", "http://onlinelibrary.wiley.com/doi/10.1111/j.1365-2672.1979.tb02580.x/references", "Nov 19, 2013", "Bevan Weir", "Title: Principles of Salmonella IsolationAuthors: R. W. S. HARVEY, T. H. PRICEJournal: Journal of Applied BacteriologyDate: 1979-2-11"));
        addItem(new Item(44.0, -79.5, "I'm trying to understand why the calculation in temperature rises was ever made incorrectly.", "10.1002/qj.2297", "Advocate", "http://onlinelibrary.wiley.com/doi/10.1002/qj.2297/abstract", "Nov 19, 2013", "Victor Ng", "Title: Coverage bias in the HadCRUT4 temperature series and its impact on recent temperature trendsAuthors: Kevin Cowtan, Robert G. WayJournal: Quarterly Journal of the Royal Meteorological SocietyDate: 2013-11"));
        addItem(new Item(44.0, -79.5, "I'm trying to understand why the calculation in temperature rises was ever made incorrectly.", "10.1002/qj.2297", "Advocate", "http://onlinelibrary.wiley.com/doi/10.1002/qj.2297/abstract", "Nov 19, 2013", "Victor Ng", "Title: Coverage bias in the HadCRUT4 temperature series and its impact on recent temperature trendsAuthors: Kevin Cowtan, Robert G. WayJournal: Quarterly Journal of the Royal Meteorological SocietyDate: 2013-11"));
        addItem(new Item(44.0, -79.5, "", "10.1002/qj.2297", "Advocate", "http://onlinelibrary.wiley.com/doi/10.1002/qj.2297/abstract", "Nov 19, 2013", "Victor Ng", "Title: Coverage bias in the HadCRUT4 temperature series and its impact on recent temperature trendsAuthors: Kevin Cowtan, Robert G. WayJournal: Quarterly Journal of the Royal Meteorological SocietyDate: 2013-11"));
        addItem(new Item(44.0, -79.5, "", "10.1016/j.urology.2010.05.009", "Advocate", "https://www.ncbi.nlm.nih.gov/pubmed/20709373", "Nov 19, 2013", "Victor Ng", "Title: Efficacy of Epididymectomy in Treatment of Chronic Epididymal Pain: A Comparison of Patients With and Without a History of VasectomyAuthors: Joo Yong Lee, Tchun Yong Lee, Hae Young Park, Hong Yong Choi, Tag Keun Yoo, Hong Sang Moon, June Hyun Han, Sung Yul Park, Seung Wook LeeJournal: UrologyDate: 2011-1"));
        addItem(new Item(44.0, -79.5, "", "10.1002/qj.2297", "Advocate", "http://onlinelibrary.wiley.com/doi/10.1002/qj.2297/abstract", "Nov 19, 2013", "Victor Ng", "Title: Coverage bias in the HadCRUT4 temperature series and its impact on recent temperature trendsAuthors: Kevin Cowtan, Robert G. WayJournal: Quarterly Journal of the Royal Meteorological SocietyDate: 2013-11"));
        addItem(new Item(-27.4710107, 153.0234489, "", "10.1177/0093650211418338", "Student", "http://crx.sagepub.com/content/40/2/215.short", "Nov 19, 2013", "Liam Pomfret", "Title: Digital Literacy and Privacy Behavior OnlineAuthors: Y. J. ParkJournal: Communication ResearchDate: 2013-2-27"));
        addItem(new Item(37.9, -122.3, "Physics homework", "10.1088/0953-8984/14/33/315", "Student", "http://iopscience.iop.org/0953-8984/14/33/315/pdf/0953-8984_14_33_315.pdf", "Nov 19, 2013", "Jihoon Kim", "Title: Colloidal suspensions, Brownian motion, molecular reality: a short historyAuthors: M D HawJournal: Journal of Physics: Condensed MatterDate: 2002-8-26"));
        addItem(new Item(-28.8, 153.3, "", "", "Researcher", "https://www.openaccessbutton.org/#top", "Nov 19, 2013", "Anna Du Chesne", ""));
        addItem(new Item(37.8715926, -122.272747, "", "10.1002/wcs.1233", "Student", "http://onlinelibrary.wiley.com/doi/10.1002/wcs.1233/full", "Nov 19, 2013", "Falk Lieder", "Title: Computational models of planningAuthors: Hector GeffnerJournal: Wiley Interdisciplinary Reviews: Cognitive ScienceDate: 2013-7-18"));
        addItem(new Item(34.8, 135.8, "I'm a scientometrician. However my institute have not bought this journal.", "10.1007/s11192-013-1173-7", "Researcher", "http://link.springer.com/article/10.1007/s11192-013-1173-7", "Nov 19, 2013", "Sho SATO", "Title: Bibliometric analysis of articles published in ISI dental journals, 2007â2011Authors: Ricardo Cartes-VelĂĄsquez, Carlos Manterola DelgadoJournal: ScientometricsDate: 2013-11-7"));
        addItem(new Item(-27.5, 153.0, "Of clinical interest ", "10.1056/NEJMoa1303154", "Advocate", "http://www.nejm.org/doi/full/10.1056/NEJMoa1303154", "Nov 19, 2013", "Ginny Barbour", "Title: Combined Angiotensin Inhibition for the Treatment of Diabetic NephropathyAuthors: Linda F. Fried, Nicholas Emanuele, Jane H. Zhang, Mary Brophy, Todd A. Conner, William Duckworth, David J. Leehey, Peter A. McCullough, Theresa O'Connor, Paul M. Palevsky, Robert F. Reilly, Stephen L. Seliger, Stuart R. Warren, Suzanne Watnick, Peter Peduzzi, Peter GuarinoJournal: New England Journal of MedicineDate: 2013-11-14"));
        addItem(new Item(40.4167754, -3.7037902, "", "", "Librarian", "https://www.openaccessbutton.org/#top", "Nov 19, 2013", "angel montes", ""));
        addItem(new Item(-34.0, 25.7, "", "", "Researcher", "http://icesjms.oxfordjournals.org/content/early/2013/10/08/icesjms.fst168.short", "Nov 19, 2013", "David Costalago", ""));
        addItem(new Item(6.4, 5.6, "Research", "", "Student", "https://www.openaccessbutton.org/#top", "Nov 21, 2013", "Mirabel Akure", "Journals"));
        addItem(new Item(19.5, -99.2, "im making a research in the mexican cinema world, and the open culture is inside.", "El Abordaje de las Ideas", "Researcher", "https://www.openaccessbutton.org/#top", "Nov 21, 2013", "El Abordaje de las Ideas", "Documentary"));
        addItem(new Item(28.635308, 77.22496, "For my research.", "10.1146/annurev.soc.012809.102629", "Academic", "http://www.annualreviews.org/doi/pdf/10.1146/annurev.soc.012809.102629", "Nov 19, 2013", "Swagato Sarkar", "Title:               A World of Standards but not a Standard World: Toward a Sociology of Standards and Standardization              *            Authors: Stefan Timmermans, Steven EpsteinJournal: Annual Review of SociologyDate: 2010-6"));
        addItem(new Item(37.4, -6.1, "", "", "Librarian", "https://www.openaccessbutton.org/#top", "Nov 19, 2013", "Macarena Garcia Parody", ""));
        addItem(new Item(50.6657252, 4.5868116, "", "10.2136/vzj2013.02.0042", "Researcher", "https://www.crops.org/publications/vzj/abstracts/12/4/vzj2013.02.0042", "Nov 19, 2013", "Guillaume Lobet", "Title: Root Water Uptake: From Three-Dimensional Biophysical Processes to Macroscopic Modeling ApproachesAuthors: Mathieu Javaux, Valentin Couvreur, Jan Vanderborght, Harry VereeckenJournal: Vadose Zone JournalDate: 2013"));
        addItem(new Item(41.2800161, 1.9766294, "research", "10.1111/j.1467-954X.1988.tb02936.x", "Student", "http://onlinelibrary.wiley.com/doi/10.1111/j.1467-954X.1988.tb02936.x/abstract", "Nov 19, 2013", "consol", "Title: Towards a typology of female entrepreneursAuthors: Stanley Cromie, John HayesJournal: The Sociological ReviewDate: 1988-2-4"));
        addItem(new Item(40.4167754, -3.7037902, "", "10.1136/oem.2008.039834", "Student", "http://oem.bmj.com/content/66/2/124.full.pdf", "Nov 19, 2013", "Alberto", "Title: Mobile phone base stations and adverse health effects: phase 2 of a cross-sectional study with measured radio frequency electromagnetic fieldsAuthors: G Berg-Beckhoff, M Blettner, B Kowall, J Breckenkamp, B Schlehofer, S Schmiedel, C Bornkessel, U Reis, P Potthoff, J SchuzJournal: Occupational and Environmental MedicineDate: 2008-9-19"));
        addItem(new Item(40.5, -3.4, "", "10.1080/10643389.2013.781935", "Student", "http://www.tandfonline.com/doi/pdf/10.1080/10643389.2013.781935#.UoskAsRWySo", "Nov 19, 2013", "Alberto", "Title: Environmental impact of radiofrequency fields from mobile phone base stationsAuthors: Luc VerschaeveJournal: Critical Reviews in Environmental Science and TechnologyDate: 2013-9-2"));
        addItem(new Item(40.4167754, -3.7037902, "", "", "Student", "http://connection.ebscohost.com/c/articles/39751330/effect-gsm-cellular-phone-radiation-behavior-honey-bees-apis-mellifera", "Nov 19, 2013", "Alberto", "Effect of GSM Cellular Phone Radiation on the Behavior of Honey Bees (Apis mellifera). Mixson, T. Andrew; Abramson, Charles I.; NoIf, Sondra L.; Johnson, Ge'Andra; Serrano, Eduardo; Wells, Harrington. Bee Culture;May2009, Vol. 137 Issue 5, Special section p22"));
        addItem(new Item(40.4167754, -3.7037902, "", "39751330", "Student", "http://connection.ebscohost.com/c/articles/39751330/effect-gsm-cellular-phone-radiation-behavior-honey-bees-apis-mellifera", "Nov 19, 2013", "Alberto", "Effect of GSM Cellular Phone Radiation on the Behavior of Honey Bees (Apis mellifera). Mixson, T. Andrew; Abramson, Charles I.; NoIf, Sondra L.; Johnson, Ge'Andra; Serrano, Eduardo; Wells, Harrington. Bee Culture;May2009, Vol. 137 Issue 5, Special section p22"));
        addItem(new Item(57.70887, 11.97456, "", "10.1021/ci400257k", "Researcher", "http://pubs.acs.org/doi/abs/10.1021/ci400257k", "Nov 19, 2013", "Fredrik Wallner", "Title: Halogen Interactions in ProteinâLigand Complexes: Implications of Halogen Bonding for Rational Drug DesignAuthors: Suman Sirimulla, Jake B. Bailey, Rahulsimham Vegesna, Mahesh NarayanJournal: Journal of Chemical Information and ModelingDate: 2013-11-13"));
        addItem(new Item(52.0212965, 8.5302966, "citizen science", "10.1111/j.1756-8765.2009.01020.x", "Librarian", "http://onlinelibrary.wiley.com/doi/10.1111/j.1756-8765.2009.01020.x/full", "Nov 19, 2013", "Christian Pietsch", "Title: Joint Action, Interactive Alignment, and DialogAuthors: Simon Garrod, Martin J. PickeringJournal: Topics in Cognitive ScienceDate: 2009-4"));
        addItem(new Item(51.5, -0.1, "", "10.3366/cor.2013.0038", "Librarian", "http://www.euppublishing.com/doi/abs/10.3366/cor.2013.0038", "Nov 19, 2013", "James Baker", "Title: Twenty-five years of Biber's Multi-Dimensional Analysis: introduction to the special issue and an interview with Douglas BiberAuthors: Eric FriginalJournal: CorporaDate: 2013-11"));
        addItem(new Item(6.4, 5.6, "Research", "", "Student", "https://www.openaccessbutton.org/#top", "Nov 21, 2013", "Mirabel Akure", "Journals"));
        addItem(new Item(55.6760968, 12.5683371, "Professional use", "10.1093/scipol/scs093", "Academic", "http://spp.oxfordjournals.org/content/39/6/751.full", "Nov 19, 2013", "Ivo Grigorov", "Title: Responsible research and innovation: From science in society to science for society, with societyAuthors: R. Owen, P. Macnaghten, J. StilgoeJournal: Science and Public PolicyDate: 2012-12-5"));
        addItem(new Item(52.0406224, -0.7594171, "", "10.1038/sj.bdj.2013.991", "Librarian", "http://www.nature.com/bdj/journal/v215/n7/full/sj.bdj.2013.991.html", "Nov 19, 2013", "Katherine Moore", "Title: Permanent dentition caries through the first half of lifeAuthors: J. M. Broadbent, L. A. Foster Page, W. M. Thomson, R. PoultonJournal: BDJDate: 2013-10-11"));
        addItem(new Item(51.5112139, -0.1198244, "My organisation is the network of UK-based non-governmental organisations working on international development, and we promote evidence-based learning on development issues.", "http://www.nber.org/papers/w19479", "Other", "http://www.nber.org/papers/w19479", "Nov 19, 2013", "Michael O'Donnell", "Girl Power: Cash Transfers and Adolescent Welfare. Evidence from a Cluster-Randomized Experiment in Malawi, by Sarah J. Baird, Ephraim Chirwa, Jacobus de Hoop, Berk Özler; NBER Working Paper No. 19479, September 2013"));
        addItem(new Item(51.7520209, -1.2577263, "Interested in seeing how many times the article has been cited.", "10.1111/jai.12193", "Other", "http://onlinelibrary.wiley.com/doi/10.1111/jai.12193/abstract", "Nov 19, 2013", "Ed Pentz", "Title:               Seasonal variation in the diet of juvenile lake sturgeon,              Acipenser fulvescens              , Rafinesque, 1817, in the Winnipeg River, Manitoba, Canada            Authors: C. C. Barth, W. G. Anderson, S. J. Peake, P. NelsonJournal: Journal of Applied IchthyologyDate: 2013-8-1"));
        addItem(new Item(51.454513, -2.58791, "", "10.1111/j.1468-0254.1994.tb00058.x", "Librarian", "http://onlinelibrary.wiley.com/doi/10.1111/j.1468-0254.1994.tb00058.x/pdf", "Nov 19, 2013", "John Kostiw", "Title: The astronomy of Macrobius in Carolingian Europe: Dungal's letter of 811 to Charles the GreatAuthors: BRUCE STANSFIELD EASTWOODJournal: Early Medieval EuropeDate: 1994-9-15"));
        addItem(new Item(53.4, -1.5, "", "10.3810/pgm.2013.01.2622", "Student", "https://postgradmed.org/doi/10.3810/pgm.2013.01.2622", "Nov 19, 2013", "Jamie Scuffell", "Title: Novel Oral Anticoagulants for Stroke Prevention in Patients With Atrial Fibrillation: Dawn of a New EraAuthors: Tahmeed Contractor, Vadim Levin, Matthew Martinez, Francis MarchlinskiJournal: Postgraduate MedicineDate: 2013-1-15"));
        addItem(new Item(48.856614, 2.3522219, "interest in subject", "10.3366/cor.2013.0047", "Other", "http://www.euppublishing.com/doi/pdfplus/10.3366/cor.2013.0047", "Nov 19, 2013", "mnavs", "Title:               Review              : Römer and Schulze (eds, 2009)              Exploring the Lexis–Grammar Interface              . Amsterdam: John Benjamins            Authors: Iona SarievaJournal: CorporaDate: 2013-11"));
        addItem(new Item(51.5112139, -0.1198244, "Item on a University reading list", "10.1191/1478088706qp063oa", "Librarian", "http://www.tandfonline.com/doi/pdf/10.1191/1478088706qp063oa#.Uos7cyfRLAA", "Nov 19, 2013", "Rebecca Shales", "Title: Using thematic analysis in psychologyAuthors: Virginia Braun, Victoria ClarkeJournal: Qualitative Research in PsychologyDate: 2006-1"));
        addItem(new Item(43.2566901, -2.9240616, "Non profitable R&D institution", "", "Librarian", "http://dialnet.unirioja.es/servlet/articulo?codigo=749170", "Nov 19, 2013", "Leonor M. Oleaga", " El ciclo transcrítico de CO2    Autores: Pedro Rufes Martínez, Angel Luis Miranda Barreras    Localización: Montajes e instalaciones: Revista técnica sobre la construcción e ingeniería de las instalaciones, ISSN 0210-184X, Año nº 33, Nº 376, 2003 , págs. 68-70"));
        addItem(new Item(43.2566901, -2.9240616, "Non-profitable R&D institution", "10.1007/s00170-012-4420-9", "Librarian", "http://link.springer.com/article/10.1007/s00170-012-4420-9", "Nov 19, 2013", "Leonor M. Oleaga", "Title: Microstructure evolution modeling of titanium alloy large ring in hot ring rollingAuthors: Min Wang, He Yang, Chun Zhang, Liang-gang GuoJournal: The International Journal of Advanced Manufacturing TechnologyDate: 2013-6-16"));
        addItem(new Item(53.5510846, 9.9936818, "", "10.1080/1573062X.2013.768683", "Researcher", "http://www.tandfonline.com/doi/abs/10.1080/1573062X.2013.768683#.Uos9-cSkqUY", "Nov 19, 2013", "Josep de Trincheria", "Title: Sustainable development and urban stormwater practiceAuthors: Annicka Cettner, Richard Ashley, Annelie Hedstrรถm, Maria ViklanderJournal: Urban Water JournalDate: 2013-5-16"));
        addItem(new Item(53.5510846, 9.9936818, "", "10.1080/1573062X.2012.682591", "Researcher", "http://www.tandfonline.com/doi/abs/10.1080/1573062X.2012.682591#.Uos9_8SkqUY", "Nov 19, 2013", "Josep de Trincheria", "Title: Cost-effectiveness-based multi-criteria optimization for sustainable rainwater utilization: A case study in ShanghaiAuthors: Yong Peng LĂź, Kai Yang, Yue Che, Zhao Yi Shang, Hui Feng Zhu, Yun JianJournal: Urban Water JournalDate: 2013-4"));
        addItem(new Item(-22.9035393, -43.2095869, "librarian trying to help students", "10.1115/1.1505854", "Librarian", "http://manufacturingscience.asmedigitalcollection.asme.org/article.aspx?articleid=1444969", "Nov 19, 2013", "Moreno Barros", "Title: Drilling of Aramid and Carbon Fiber Polymer CompositesAuthors: M. S. Won, C. K. H. DharanJournal: Journal of Manufacturing Science and EngineeringDate: 2002"));
        addItem(new Item(37.3880961, -5.9823299, "important for my research", "10.1111/1755-0998.12184", "Researcher", "http://onlinelibrary.wiley.com/doi/10.1111/1755-0998.12184/abstract", "Nov 19, 2013", "Francisco Rodriguez", "Title: Ecological niche models in phylogeographic studies: applications, advances and precautionsAuthors: Diego F. Alvarado-Serrano, L. Lacey KnowlesJournal: Molecular Ecology ResourcesDate: 2013-11-16"));
        addItem(new Item(-22.9035393, -43.2095869, "librarian trying to help students research", "10.1115/1.1448317", "Librarian", "http://manufacturingscience.asmedigitalcollection.asme.org/article.aspx?articleid=1442509", "Nov 19, 2013", "Moreno Barros", "Title: Chisel Edge and Pilot Hole Effects in Drilling Composite LaminatesAuthors: M. S. Won, C. K. H. DharanJournal: Journal of Manufacturing Science and EngineeringDate: 2002"));
        addItem(new Item(40.65, -73.95, "", "", "Student", "http://www.sciencedirect.com/science/article/pii/S0304418197000134", "Nov 19, 2013", "Tanya", ""));
        addItem(new Item(52.2, 0.1, "For writing my PhD", "10.1558/refm.v11.131", "Academic", "https://essential.metapress.com/content/532205w226n03441/resource-secured/?target=fulltext.pdf", "Nov 19, 2013", "Liesbeth Corens", "Title: Charity, Community and Reformation PropagandaAuthors: Lucy E. C. WoodingJournal: ReformationDate: 2006-6-1"));
        addItem(new Item(-22.9035393, -43.2095869, "librarian trying to help students research", "10.1177/002199839502901503", "Librarian", "http://jcm.sagepub.com/content/29/15/1988.full.pdf+html", "Nov 19, 2013", "Moreno Barros", "Title: Delamination-Free and High Efficiency Drilling of Carbon Fiber Reinforced PlasticsAuthors: K. Y. Park, J. H. Choi, D. G. LeeJournal: Journal of Composite MaterialsDate: 1995-10-1"));
        addItem(new Item(55.9, -4.3, "", "", "Student", "https://live.arthritisresearchuk.org/CookieAuth.dll?GetLogon?curl=Z2FsitecoreZ2FshellZ2FControlsZ2FRichZ2520TextZ2520EditorZ2FZ2FZ7EZ2FmediaZ2FImagesZ2FEducational-resourcesZ2FReportsZ2520imagesZ2FHO6_3Z2FHO6_3Z2520figureZ25209.ashx&reason=2&formdir=3", "Nov 19, 2013", "Elizabeth Thomas", ""));
        addItem(new Item(55.9, -4.3, "", "", "Student", "https://live.arthritisresearchuk.org/CookieAuth.dll?GetLogon?curl=Z2FsitecoreZ2FshellZ2FControlsZ2FRichZ2520TextZ2520EditorZ2FZ2FZ7EZ2FmediaZ2FImagesZ2FEducational-resourcesZ2FReportsZ2520imagesZ2FHO6_3Z2FHO6_3Z2520figureZ25209.ashx&reason=2&formdir=3", "Nov 19, 2013", "Elizabeth Thomas", ""));
        addItem(new Item(-22.9035393, -43.2095869, "librarian trying to help students research", "10.1080/095465591009359", "Librarian", "http://www.tandfonline.com/doi/abs/10.1080/095465591009359#.UotGgdI_snM", "Nov 19, 2013", "Moreno Barros", "Title: Six Rather Unusual Propositions about TerrorismAuthors: John MuellerJournal: Terrorism and Political ViolenceDate: 2005-12"));
        addItem(new Item(-22.9035393, -43.2095869, "librarian trying to help students research", "10.1002/apmc.1993.052110108", "Librarian", "http://onlinelibrary.wiley.com/doi/10.1002/apmc.1993.052110108/abstract", "Nov 19, 2013", "Moreno Barros", "Authors: Alexander ProĂ, Peter Marquardt, Karl-Heinz Reichert, Wolfgang Nentwig, Thomas KnaufJournal: Angewandte Makromolekulare ChemieDate: 1993-10"));
        addItem(new Item(-22.9035393, -43.2095869, "librarian trying to help students research", "10.1080/13825580903281308", "Librarian", "http://www.tandfonline.com/doi/abs/10.1080/13825580903281308?tab=permissions#tabModule", "Nov 19, 2013", "Moreno Barros", "Title: Are Older Adults More Social Than Younger Adults? Social Importance Increases Older Adults' Prospective Memory PerformanceAuthors: Mareike Altgassen, Matthias Kliegel, Maria Brandimonte, Pina FilippelloJournal: Aging, Neuropsychology, and CognitionDate: 2010-4-21"));
        addItem(new Item(40.463667, -3.74922, "", "10.1007/BF02441916", "Librarian", "http://link.springer.com/article/10.1007/BF02441916", "Nov 19, 2013", "Teresa-Maria Figuerola-Curcó", "Title: Dynamic relationship between EMG and torque at the human ankle: Variation with contraction level and modulationAuthors: W. F. Genadry, R. E. Kearney, I. W. HunterJournal: Medical & Biological Engineering & ComputingDate: 1988-9"));
        addItem(new Item(54.5259614, 15.2551187, "", "10.1007/BF02441916", "Librarian", "http://link.springer.com/article/10.1007/BF02441916", "Nov 19, 2013", "Teresa-Maria Figuerola-Curcó", "Title: Dynamic relationship between EMG and torque at the human ankle: Variation with contraction level and modulationAuthors: W. F. Genadry, R. E. Kearney, I. W. HunterJournal: Medical & Biological Engineering & ComputingDate: 1988-9"));
        addItem(new Item(53.3498053, -6.2603097, "research", "10.1016/S0166-4972(00)00012-2", "Librarian", "http://www.sciencedirect.com/science/article/pii/S0166497200000122", "Nov 19, 2013", "debora zorzi", "Title: Economic issues in recycling end-of-life vehiclesAuthors: Klaus Bellmann, Anshuman KhareJournal: TechnovationDate: 2000-12"));
        addItem(new Item(-33.30566, 26.52453, "Needed for a postgrad course on Climate Change", "10.1038/nclimate2051", "Student", "http://www.nature.com/nclimate/journal/vaop/ncurrent/full/nclimate2051.html", "Nov 19, 2013", "Eileen Shepherd", "Title: Robust spatially aggregated projections of climate extremesAuthors: E. M. Fischer, U. Beyerle, R. KnuttiJournal: Nature Climate ChangeDate: 2013-11-17"));
        addItem(new Item(55.8, -4.1, "I need this paper for my research", "10.1016/j.yexcr.2004.09.026", "Student", "http://www.sciencedirect.com/science/article/pii/S0014482704005762", "Nov 19, 2013", "Graham Steel", "Title: Myocilin binding to Hep II domain of fibronectin inhibits cell spreading and incorporation of paxillin into focal adhesionsAuthors: Donna M. Peters, Kathleen Herbert, Brenda Biddick, Jennifer A. PetersonJournal: Experimental Cell ResearchDate: 2005-2"));
        addItem(new Item(51.5112139, -0.1198244, "I study biological oscillators and want to know more!", "10.1038/ncomms3769", "Researcher", "http://www.nature.com/ncomms/2013/131114/ncomms3769/full/ncomms3769.html", "Nov 19, 2013", "Alexis Webb", "Title: Circadian rhythms in Mexican blind cavefish Astyanax mexicanus in the lab and in the fieldAuthors: Andrew Beale, Christophe Guibal, T. Katherine Tamai, Linda Klotz, Sophie Cowen, Elodie Peyric, VĂ­ctor H. Reynoso, Yoshiyuki Yamamoto, David WhitmoreJournal: Nature CommunicationsDate: 2013-11-14"));
        addItem(new Item(50.82253, -0.137163, "Research into mitochondrial replacement therapy (so-called 3-person IVF)", "", "Researcher", "http://journal.nzma.org.nz/journal/abstract.php?id=5895", "Nov 19, 2013", "Edward Morrow", "Numerical identity: the creation of tri-parental embryos to correct inherited mitochondrial diseaseMichael Legge, Ruth FitzgeraldThe New Zealand Medical Journal"));
    }

    public Intent onShareButtonPressed(Resources resources) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.map_share_text));
        shareIntent.putExtra(Intent.EXTRA_TEXT, WEB_MAP_URL);
        shareIntent.setType("text/plain");

        return shareIntent;
    }

    public void updateShareIntent() {
        Intent shareIntent = onShareButtonPressed(getResources());
        mCallback.onShareIntentUpdated(shareIntent);
    }
}
